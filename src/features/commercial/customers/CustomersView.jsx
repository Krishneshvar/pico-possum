import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Plus, Phone, MapPin, Edit2, Trash2, Receipt } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatDate, formatPaise, getCurrencySymbol } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import ExportButton from '../../../components/common/ExportButton';
import { exportToCsv, exportToXlsx, escapeCsv } from '../../../lib/exportUtils';
import { useWorkspaceStore } from '../../../stores/workspaceStore';
import CustomerModal from './CustomerModal';
import CustomerProfileModal from './CustomerProfileModal';
import { strings } from '../../../lib/strings';

export default function CustomersView() {
  const queryClient = useQueryClient();
  const { openOrFocus } = useWorkspaceStore();

  const [modalOpen, setModalOpen] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState(null);
  const [viewingCustomer, setViewingCustomer] = useState(null);

  const { data: generalSettings } = useQuery({
    queryKey: ['generalSettings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });
  const currencySymbol = generalSettings?.currencySymbol || getCurrencySymbol();

  const { data: customers = [], isLoading } = useQuery({
    queryKey: ['customers'],
    queryFn: () => tauriApi.getCustomers(null),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteCustomer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      toast.success(strings.customers.toasts.deleted);
    },
    onError: (err) =>
      toast.error(strings.customers.toasts.deleteFailed(err), {
        title: strings.customers.toasts.deleteFailedTitle,
      }),
  });

  const handleOpenCreate = () => {
    setEditingCustomer(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (customer) => {
    setEditingCustomer(customer);
    setModalOpen(true);
  };

  const handleDelete = async (customer) => {
    const { confirmed } = await confirmDialog({
      title: strings.customers.dialogs.deleteTitle(customer.name),
      message: strings.customers.dialogs.deleteMessage(customer.name),
      confirmLabel: strings.customers.dialogs.deleteConfirm,
      cancelLabel: strings.common.actions.cancel,
      tone: 'danger',
    });
    if (confirmed) {
      deleteMutation.mutate(customer.id);
    }
  };

  const handleOpenBillDetail = (sale) => {
    const invNumber = sale.invoiceNumber ?? sale.invoice_number;
    openOrFocus(
      strings.customers.billTabTitle(invNumber),
      'bill-detail',
      { saleId: sale.id, invoiceNumber: invNumber },
      `bill-${sale.id}`
    );
  };

  const handleExportCsv = async () => {
    if (!customers.length) {
      toast.warning(strings.customers.toasts.nothingToExport);
      return;
    }

    const headers = [
      strings.customers.export.headers.customerName,
      strings.customers.export.headers.phone,
      strings.customers.export.headers.address,
      strings.customers.export.headers.totalBills,
      strings.customers.export.headers.totalSpent(currencySymbol),
      strings.customers.export.headers.lastVisit,
    ];

    const rows = customers.map((c) => {
      const totalSales = c.totalSales ?? c.total_sales ?? 0;
      const totalSpent = c.totalSpentPaise ?? c.total_spent_paise ?? 0;
      const lastVisit = c.lastSaleDate ?? c.last_sale_date;
      return [
        escapeCsv(c.name || ''),
        escapeCsv(c.phone || ''),
        escapeCsv(c.address || ''),
        escapeCsv(totalSales),
        escapeCsv((totalSpent / 100).toFixed(2)),
        escapeCsv(lastVisit ? formatDate(lastVisit) : ''),
      ];
    });

    const dateStr = new Date().toISOString().split('T')[0];
    await exportToCsv({
      filename: `customers_${dateStr}.csv`,
      headers,
      rows,
      count: customers.length,
      entityName: strings.customers.export.entityName,
    });
  };

  const handleExportXlsx = async () => {
    if (!customers.length) {
      toast.warning(strings.customers.toasts.nothingToExport);
      return;
    }

    const headers = [
      strings.customers.export.headers.customerName,
      strings.customers.export.headers.phone,
      strings.customers.export.headers.address,
      strings.customers.export.headers.totalBills,
      strings.customers.export.headers.totalSpent(currencySymbol),
      strings.customers.export.headers.lastVisit,
    ];

    const rows = customers.map((c) => {
      const totalSales = c.totalSales ?? c.total_sales ?? 0;
      const totalSpent = c.totalSpentPaise ?? c.total_spent_paise ?? 0;
      const lastVisit = c.lastSaleDate ?? c.last_sale_date;
      return [
        c.name || '',
        c.phone || '',
        c.address || '',
        totalSales,
        parseFloat((totalSpent / 100).toFixed(2)),
        lastVisit ? formatDate(lastVisit) : strings.common.states.emptyValue,
      ];
    });

    const colWidths = [
      { wch: 25 },
      { wch: 18 },
      { wch: 30 },
      { wch: 14 },
      { wch: 18 },
      { wch: 14 },
    ];
    const dateStr = new Date().toISOString().split('T')[0];
    await exportToXlsx({
      filename: `customers_${dateStr}.xlsx`,
      sheetName: strings.customers.export.sheetName,
      headers,
      rows,
      colWidths,
      count: customers.length,
      entityName: strings.customers.export.entityName,
    });
  };

  const columns = [
    {
      key: 'name',
      label: strings.customers.columns.name,
      className: 'font-semibold text-text-primary text-xs',
      render: (r) => (
        <span
          onClick={(e) => {
            e.stopPropagation();
            setViewingCustomer(r);
          }}
          className="hover:underline cursor-pointer text-brand"
        >
          {r.name}
        </span>
      ),
    },
    {
      key: 'phone',
      label: strings.customers.columns.phone,
      className: 'font-mono text-text-muted text-xs',
      render: (r) => r.phone || strings.common.states.emptyValue,
    },
    {
      key: 'totalBills',
      label: strings.customers.columns.billsCount,
      headerClassName: 'text-center',
      className: 'text-center text-xs font-mono',
      render: (r) => (
        <span className="px-2 py-0.5 rounded-full bg-surface-secondary border border-border-default text-text-secondary text-[11px]">
          {r.totalSales ?? r.total_sales ?? 0}
        </span>
      ),
    },
    {
      key: 'totalSpentPaise',
      label: strings.customers.columns.totalSpent,
      headerClassName: 'text-right',
      className: 'text-right financial-value font-bold text-text-primary text-xs',
      render: (r) => formatPaise(r.totalSpentPaise ?? r.total_spent_paise ?? 0),
    },
    {
      key: 'lastVisit',
      label: strings.customers.columns.lastVisit,
      className: 'text-text-muted text-xs whitespace-nowrap',
      render: (r) => {
        const lastVisit = r.lastSaleDate ?? r.last_sale_date;
        return lastVisit ? formatDate(lastVisit) : strings.common.states.emptyValue;
      },
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-border-default bg-surface flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
            <Users className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-text-primary">
              {strings.customers.view.title}
            </h2>
            <p className="text-[11px] text-text-muted">
              {strings.customers.view.subtitle}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <ImportCsvButton
            entity="customers"
            onComplete={() => {
              queryClient.invalidateQueries({ queryKey: ['customers'] });
            }}
          />

          <ExportButton
            onExportCsv={handleExportCsv}
            onExportXlsx={handleExportXlsx}
          />

          <button
            type="button"
            onClick={handleOpenCreate}
            className="px-3.5 py-1.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-bold text-xs flex items-center gap-1.5 shadow-xs transition-colors cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>{strings.customers.actions.addCustomer}</span>
          </button>
        </div>
      </div>

      {/* Directory Table */}
      <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
        <DataTable
          columns={columns}
          persistKey="customers"
          data={customers}
          keyFn={(r) => r.id}
          searchKeys={['name', 'phone', 'address']}
          isLoading={isLoading}
          emptyMessage={strings.customers.view.empty}
          onRowClick={(row) => setViewingCustomer(row)}
          actions={(row) => (
            <DropdownActions
              items={[
                {
                  label: strings.customers.actions.purchaseHistory,
                  icon: <Receipt className="w-3.5 h-3.5" />,
                  onClick: () => setViewingCustomer(row),
                },
                {
                  label: strings.customers.actions.editCustomer,
                  icon: <Edit2 className="w-3.5 h-3.5" />,
                  onClick: () => handleOpenEdit(row),
                },
                'divider',
                {
                  label: strings.customers.actions.deleteCustomer,
                  icon: <Trash2 className="w-3.5 h-3.5" />,
                  danger: true,
                  onClick: () => handleDelete(row),
                },
              ]}
            />
          )}
        />
      </div>

      {/* ── Customer Profile & Purchase History Modal ── */}
      <CustomerProfileModal
        customer={viewingCustomer}
        onClose={() => setViewingCustomer(null)}
        onOpenBillDetail={handleOpenBillDetail}
      />

      {/* ── Add / Edit Customer Modal ── */}
      <CustomerModal
        open={modalOpen}
        customer={editingCustomer}
        onClose={() => {
          setModalOpen(false);
          setEditingCustomer(null);
        }}
      />
    </div>
  );
}
