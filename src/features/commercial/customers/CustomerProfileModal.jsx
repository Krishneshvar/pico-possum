import { useState, useMemo, useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { X, ArrowRight } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatCurrency, formatDate, formatDateTime } from '../../../lib/formatters';
import SortableTh from '../../../components/common/SortableTh';
import { nextSortState, sortRows } from '../../../lib/sort';
import { strings } from '../../../lib/strings';

export default function CustomerProfileModal({
  customer,
  onClose,
  onOpenBillDetail,
}) {
  const [historySort, setHistorySort] = useState({ key: 'createdAt', dir: 'desc' });
  const closeBtnRef = useRef(null);

  useEffect(() => {
    if (customer) {
      const timer = setTimeout(() => {
        closeBtnRef.current?.focus();
      }, 50);
      return () => clearTimeout(timer);
    }
  }, [customer?.id]);

  useEffect(() => {
    if (!customer) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose?.();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [customer, onClose]);

  const { data: customerSales = [], isLoading: loadingCustomerSales } = useQuery({
    queryKey: ['customerSales', customer?.id],
    queryFn: () => tauriApi.getCustomerSales(customer.id),
    enabled: Boolean(customer?.id),
  });

  const sortedCustomerSales = useMemo(() => {
    return sortRows(customerSales, historySort, (row, key) => row[key] ?? row[key.replace(/([A-Z])/g, '_$1').toLowerCase()]);
  }, [customerSales, historySort]);

  const customerStats = useMemo(() => {
    if (!customerSales || customerSales.length === 0) {
      return { totalOrders: 0, totalSpent: 0, avgBill: 0, lastOrder: null };
    }
    const nonCancelled = customerSales.filter((s) => s.status !== 'CANCELLED');
    const totalOrders = nonCancelled.length;
    const totalSpent = nonCancelled.reduce((sum, s) => sum + (s.totalCents ?? s.total_cents ?? 0), 0);
    const avgBill = totalOrders > 0 ? Math.round(totalSpent / totalOrders) : 0;
    const lastOrder = (customerSales[0]?.createdAt ?? customerSales[0]?.created_at) || null;
    return { totalOrders, totalSpent, avgBill, lastOrder };
  }, [customerSales]);

  if (!customer) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
      <div className="bg-surface border border-border-default rounded-2xl max-w-2xl w-full p-6 space-y-4 shadow-xl max-h-[90vh] flex flex-col text-text-primary animate-scale-in">
        <div className="flex justify-between items-center border-b border-border-default pb-3 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-brand-subtle text-brand flex items-center justify-center font-bold text-base">
              {(customer.name || strings.customers.profile.fallbackInitial)[0].toUpperCase()}
            </div>
            <div>
              <h3 className="font-bold text-base text-text-primary">{customer.name}</h3>
              <div className="flex items-center gap-3 text-xs text-text-muted mt-0.5">
                {customer.phone && (
                  <span className="font-mono">
                    {strings.customers.profile.phone(customer.phone)}
                  </span>
                )}
                {customer.address && <span>• {customer.address}</span>}
              </div>
            </div>
          </div>
          <button
            ref={closeBtnRef}
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary cursor-pointer focus:outline-none focus:ring-2 focus:ring-brand"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Compact Lifetime Stats Grid */}
        <div className="grid grid-cols-4 gap-2.5 text-xs shrink-0">
          <div className="p-3 bg-surface-secondary rounded-xl border border-border-default text-center">
            <span className="text-text-muted block text-[11px]">
              {strings.customers.profile.stats.totalOrders}
            </span>
            <span className="text-base font-bold text-text-primary font-mono mt-0.5 block">
              {customerStats.totalOrders}
            </span>
          </div>
          <div className="p-3 bg-surface-secondary rounded-xl border border-border-default text-center">
            <span className="text-text-muted block text-[11px]">
              {strings.customers.profile.stats.totalSpent}
            </span>
            <span className="text-base font-bold text-text-primary financial-value mt-0.5 block">
              {formatCurrency(customerStats.totalSpent)}
            </span>
          </div>
          <div className="p-3 bg-surface-secondary rounded-xl border border-border-default text-center">
            <span className="text-text-muted block text-[11px]">
              {strings.customers.profile.stats.averageOrder}
            </span>
            <span className="text-base font-bold text-text-primary financial-value mt-0.5 block">
              {formatCurrency(customerStats.avgBill)}
            </span>
          </div>
          <div className="p-3 bg-surface-secondary rounded-xl border border-border-default text-center">
            <span className="text-text-muted block text-[11px]">
              {strings.customers.profile.stats.lastOrder}
            </span>
            <span className="text-xs font-bold text-text-secondary mt-1 block truncate">
              {customerStats.lastOrder
                ? formatDate(customerStats.lastOrder)
                : strings.common.states.none}
            </span>
          </div>
        </div>

        {/* Invoices List */}
        <div className="flex-1 overflow-y-auto border border-border-default rounded-xl bg-surface shadow-2xs">
          <table className="w-full text-left border-collapse text-xs select-text">
            <thead className="bg-table-header-bg text-[11px] font-semibold text-table-header-text uppercase tracking-wider sticky top-0 border-b border-table-header-border z-10 shadow-2xs">
              <tr>
                <SortableTh
                  sortKey="invoiceNumber"
                  sort={historySort}
                  onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                  className="py-2.5 px-3.5 pl-4"
                >
                  {strings.common.labels.invoiceNumber}
                </SortableTh>
                <SortableTh
                  sortKey="createdAt"
                  sort={historySort}
                  onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                  className="py-2.5 px-3.5"
                >
                  {strings.common.labels.date}
                </SortableTh>
                <SortableTh
                  sortKey="paymentMethod"
                  sort={historySort}
                  onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                  className="py-2.5 px-3.5"
                >
                  {strings.customers.profile.history.columns.payment}
                </SortableTh>
                <SortableTh
                  sortKey="status"
                  sort={historySort}
                  onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                  align="center"
                  className="py-2.5 px-3.5"
                >
                  {strings.common.labels.status}
                </SortableTh>
                <SortableTh
                  sortKey="totalCents"
                  sort={historySort}
                  onSort={(key) => setHistorySort((p) => nextSortState(p, key))}
                  align="right"
                  className="py-2.5 px-3.5"
                >
                  {strings.common.labels.amount}
                </SortableTh>
                <th className="py-2.5 pl-3.5 pr-4 text-right w-20">
                  {strings.customers.profile.history.columns.action}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-default">
              {loadingCustomerSales ? (
                <tr>
                  <td colSpan="6" className="py-10 text-center text-text-muted">
                    <div className="inline-block w-4 h-4 border-2 border-brand border-t-transparent rounded-full animate-spin" />
                  </td>
                </tr>
              ) : sortedCustomerSales.length === 0 ? (
                <tr>
                  <td colSpan="6" className="py-10 text-center text-text-muted font-sans">
                    {strings.customers.profile.history.empty}
                  </td>
                </tr>
              ) : (
                sortedCustomerSales.map((sale) => (
                  <tr key={sale.id} className="hover:bg-surface-secondary/70 transition-colors">
                    <td className="py-2 px-3.5 pl-4 font-bold font-mono text-text-primary">
                      {sale.invoiceNumber ?? sale.invoice_number}
                    </td>
                    <td className="py-2 px-3.5 text-text-muted text-[11px]">
                      {formatDateTime(sale.createdAt ?? sale.created_at)}
                    </td>
                    <td className="py-2 px-3.5">
                      <span className="px-1.5 py-0.5 rounded bg-surface-secondary text-text-secondary text-[10px] uppercase border border-border-default">
                        {(sale.paymentMethod ?? sale.payment_method)
                          ? (sale.paymentMethod ?? sale.payment_method).replace(/_/g, ' ')
                          : strings.customers.profile.history.defaultPaymentMethod}
                      </span>
                    </td>
                    <td className="py-2 px-3.5 text-center">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase ${
                          sale.status === 'CANCELLED'
                            ? 'bg-danger-bg text-danger'
                            : 'bg-success-bg text-success'
                        }`}
                      >
                        {sale.status}
                      </span>
                    </td>
                    <td className="py-2 px-3.5 text-right font-bold financial-value text-text-primary">
                      {formatCurrency(sale.totalCents ?? sale.total_cents)}
                    </td>
                    <td className="py-2 pl-3.5 pr-4 text-right">
                      <button
                        type="button"
                        onClick={() => {
                          onOpenBillDetail(sale);
                          onClose();
                        }}
                        className="px-2 py-1 rounded bg-surface hover:bg-surface-secondary text-text-primary border border-border-default text-[11px] inline-flex items-center gap-1 transition-colors cursor-pointer"
                      >
                        <span>{strings.common.actions.open}</span>
                        <ArrowRight className="w-3 h-3 text-brand" />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="flex justify-end pt-2 border-t border-border-default shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg text-xs font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
          >
            {strings.common.actions.close}
          </button>
        </div>
      </div>
    </div>
  );
}
