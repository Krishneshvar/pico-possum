import { useState, useRef, useMemo } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import {
  ArrowLeft,
  Printer,
  RotateCcw,
  AlertTriangle,
  Receipt,
  CheckCircle2,
  XCircle,
  Pencil,
  X,
  Save,
  Clock,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatDateTime, getCurrencySymbol } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import { executeDirectPrint } from '../../../components/receipt/directPrint';
import PosBillEditor from '../pos/PosBillEditor';
import CustomerModal from '../customers/CustomerModal';
import BillDetailReadView from './BillDetailReadView';
import { useWorkspaceStore } from '../../../stores/workspaceStore';
import { strings } from '../../../lib/strings';
import { useBillEditDraft } from './useBillEditDraft';

const b = strings.bills;

export default function BillDetailView({ saleId, invoiceNumber, editRequest }) {
  const currencySymbol = getCurrencySymbol();
  const { openOrFocus, closeTab, activeTabId } = useWorkspaceStore();

  const [reprintSale, setReprintSale] = useState(null);
  const [lineSort, setLineSort] = useState({ key: null, dir: null });
  const searchInputRef = useRef(null);
  const amountTenderedInputRef = useRef(null);

  const lookupKey = saleId || invoiceNumber;

  const { data: saleDetails, isLoading, isError } = useQuery({
    queryKey: ['saleDetails', lookupKey],
    queryFn: () => tauriApi.getSaleDetails(lookupKey),
    enabled: !!lookupKey,
  });

  const { data: billSettings } = useQuery({
    queryKey: ['billSettings'],
    queryFn: () => tauriApi.getBillSettings(),
  });

  const { data: generalSettings } = useQuery({
    queryKey: ['generalSettings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  const { data: catalogProducts = [] } = useQuery({
    queryKey: ['products'],
    queryFn: () => tauriApi.getProducts(null, null),
    staleTime: 30_000,
  });

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => tauriApi.getCategories(),
  });

  const { data: customers = [] } = useQuery({
    queryKey: ['customers'],
    queryFn: () => tauriApi.getCustomers(),
  });

  const allowNegativeStock = generalSettings?.allowNegativeStock ?? generalSettings?.allow_negative_stock ?? false;

  const productStockById = useMemo(() => {
    const map = new Map();
    for (const p of catalogProducts) map.set(p.id, p);
    return map;
  }, [catalogProducts]);

  const stockAllowanceByProduct = useMemo(() => {
    const map = new Map();
    for (const it of saleDetails?.items || []) {
      const pId = it.productId ?? it.product_id;
      map.set(pId, (map.get(pId) || 0) + it.quantity);
    }
    return map;
  }, [saleDetails]);

  const draft = useBillEditDraft({
    saleDetails,
    saleId,
    catalogProducts,
    generalSettings,
    stockAllowanceByProduct,
    productStockById,
    allowNegativeStock,
    searchInputRef,
    amountTenderedInputRef,
    editRequest,
  });

  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }) => tauriApi.cancelSale(id, reason),
    onSuccess: () => {
      draft.invalidateSalesAndAnalytics();
      toast.success(b.toasts.saleCancelledReturned);
    },
    onError: (err) =>
      toast.error(b.toasts.cancelFailed(err), { title: b.toasts.cancelFailedTitle }),
  });

  const handleReprint = async () => {
    if (!saleDetails) return;
    setReprintSale(saleDetails);
    await executeDirectPrint({
      sale: saleDetails.sale,
      items: saleDetails.items,
      billSettings,
      generalSettings,
    });
  };

  const handleBack = () => {
    openOrFocus(b.history.title, 'bills');
    if (activeTabId) closeTab(activeTabId);
  };

  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-canvas text-text-muted">
        <div className="flex items-center gap-3">
          <div className="w-5 h-5 border-2 border-brand border-t-transparent rounded-full animate-spin" />
          <span className="text-sm">{b.detail.loading}</span>
        </div>
      </div>
    );
  }

  if (isError || !saleDetails) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center bg-canvas text-text-secondary gap-3">
        <Receipt className="w-10 h-10 text-text-muted" />
        <p className="text-sm">{b.detail.notFound(saleId)}</p>
        <button
          type="button"
          onClick={handleBack}
          className="px-3.5 py-1.5 rounded-lg bg-surface hover:bg-surface-secondary border border-border-default text-text-primary text-xs font-semibold cursor-pointer"
        >
          {b.actions.returnToHistory}
        </button>
      </div>
    );
  }

  const { sale, items } = saleDetails;
  const isCancelled = sale.status === 'CANCELLED';
  const isLegacy = sale.status === 'LEGACY';

  const handleCancelSale = async () => {
    const invNo = sale.invoiceNumber ?? sale.invoice_number;
    const { confirmed, value } = await confirmDialog({
      title: b.dialogs.cancelBill.title(invNo),
      message: b.dialogs.cancelBill.message(invNo),
      input: {
        label: b.dialogs.cancelBill.reasonLabel,
        defaultValue: b.dialogs.cancelBill.reasonDefault,
        placeholder: b.dialogs.cancelBill.reasonPlaceholder,
        required: true,
      },
      confirmLabel: b.dialogs.cancelBill.confirm,
      cancelLabel: b.dialogs.cancelBill.cancel,
      tone: 'danger',
      icon: RotateCcw,
    });

    if (confirmed) {
      cancelMutation.mutate({
        id: sale.id,
        reason: value || b.dialogs.cancelBill.reasonDefault,
      });
    }
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* ── Top Header ── */}
      <div className="h-14 px-6 border-b border-border-default bg-surface flex items-center justify-between shrink-0">
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={draft.editing ? draft.cancelEdit : handleBack}
            className="p-1.5 rounded-lg bg-surface-secondary hover:bg-surface border border-border-default text-text-secondary hover:text-text-primary transition-colors cursor-pointer"
            title={draft.editing ? b.detail.discardEdit : b.detail.backToHistory}
          >
            {draft.editing ? <X className="w-4 h-4" /> : <ArrowLeft className="w-4 h-4" />}
          </button>
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
              <Receipt className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h2 className="text-sm font-bold text-text-primary font-mono tracking-wide">
                  {b.detail.heading(sale.invoiceNumber ?? sale.invoice_number ?? invoiceNumber)}
                </h2>
                {draft.editing ? (
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border bg-warning-bg text-warning border-warning/30">
                    <Pencil className="w-3 h-3" /> {b.detail.editingMode}
                  </span>
                ) : (
                  <span
                    className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border ${
                      isCancelled
                        ? 'bg-danger-bg text-danger border-danger/30'
                        : isLegacy
                        ? 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border-purple-500/30'
                        : 'bg-success-bg text-success border-success/30'
                    }`}
                  >
                    {isCancelled ? (
                      <XCircle className="w-3 h-3" />
                    ) : isLegacy ? (
                      <Clock className="w-3 h-3" />
                    ) : (
                      <CheckCircle2 className="w-3 h-3" />
                    )}
                    {isLegacy ? b.status.legacy : sale.status === 'PARTIALLY_PAID' ? 'Partially Paid' : sale.status}
                  </span>
                )}
              </div>
              <p className="text-[11px] text-text-muted font-mono">{formatDateTime(sale.createdAt ?? sale.created_at)}</p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {!isCancelled && !isLegacy && !draft.editing && (
            <>
              <button
                type="button"
                onClick={draft.enterEdit}
                className="px-3.5 py-1.5 rounded-lg text-text-secondary hover:text-text-primary bg-surface hover:bg-surface-secondary text-xs font-semibold flex items-center gap-1.5 transition-colors border border-border-default cursor-pointer shadow-2xs"
              >
                <Pencil className="w-3.5 h-3.5 text-text-muted" />
                {b.actions.amendBill}
              </button>
              <button
                type="button"
                onClick={handleCancelSale}
                className="px-3.5 py-1.5 rounded-lg text-danger hover:bg-danger-bg text-xs font-semibold flex items-center gap-1.5 transition-colors border border-danger/30 cursor-pointer shadow-2xs"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                {b.actions.cancelReturn}
              </button>
            </>
          )}

          {draft.editing && (
            <>
              <button
                type="button"
                onClick={draft.cancelEdit}
                className="px-3.5 py-1.5 rounded-lg text-text-secondary hover:text-text-primary hover:bg-surface-secondary text-xs font-medium border border-border-default transition-colors cursor-pointer"
              >
                {b.actions.discard}
              </button>
              <button
                type="button"
                onClick={draft.handleSaveEdit}
                disabled={draft.updateMutation.isPending || !draft.draftBill?.items.length || !draft.isDraftPaymentValid}
                className="px-4 py-1.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-bold text-xs flex items-center gap-2 shadow-xs transition-colors disabled:opacity-50 cursor-pointer"
              >
                <Save className="w-3.5 h-3.5" />
                {draft.updateMutation.isPending ? b.actions.saving : b.actions.save}
              </button>
            </>
          )}

          {!draft.editing && (
            <button
              type="button"
              onClick={handleReprint}
              className="px-4 py-1.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-bold text-xs flex items-center gap-2 shadow-xs transition-colors cursor-pointer"
            >
              <Printer className="w-3.5 h-3.5" />
              {b.actions.reprintReceipt}
            </button>
          )}
        </div>
      </div>

      {/* ── Content: POS Editor in Edit Mode vs Two-Column Layout in Read Mode ── */}
      {draft.editing && draft.draftBill ? (
        <div className="flex-1 flex flex-col overflow-hidden relative">
          {draft.editError && (
            <div className="p-3 mx-4 mt-2 rounded-xl bg-danger-bg border border-danger/30 flex items-start gap-2.5 text-xs text-danger shrink-0">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{draft.editError}</span>
            </div>
          )}

          <PosBillEditor
            bill={draft.draftBill}
            totals={draft.draftTotals}
            products={draft.editProducts}
            categories={categories}
            customers={customers}
            stockProducts={catalogProducts}
            allowNegativeStock={allowNegativeStock}
            stockAllowanceByProduct={stockAllowanceByProduct}
            currencySymbol={currencySymbol}
            searchQuery={draft.editSearchQuery}
            onSearchQueryChange={draft.setEditSearchQuery}
            selectedCategory={draft.editSelectedCategory}
            onSelectedCategoryChange={draft.setEditSelectedCategory}
            searchFocused={draft.editSearchFocused}
            onSearchFocusedChange={draft.setEditSearchFocused}
            searchInputRef={searchInputRef}
            amountTenderedInputRef={amountTenderedInputRef}
            onAddItem={draft.addProductToDraft}
            onUpdateItemQty={draft.updateDraftItemQty}
            onUpdateItemPrice={draft.updateDraftItemPrice}
            onUpdateItemDiscount={draft.updateDraftItemDiscount}
            onRemoveItem={draft.removeDraftItem}
            onSetCustomer={draft.setDraftCustomer}
            onSetDiscount={draft.setDraftDiscount}
            onSetPaymentMethod={draft.setDraftPaymentMethod}
            onSetFullPayment={draft.setDraftFullPayment}
            onSetAmountTendered={draft.setDraftAmountTendered}
            onRequestAddCustomer={() => draft.setCustomerModalOpen(true)}
            notes={draft.draftBill.notes || ''}
            onNotesChange={draft.setDraftNotes}
            primaryAction={{
              label: b.actions.save,
              pendingLabel: b.actions.savingChanges,
              pending: draft.updateMutation.isPending,
              disabled: draft.updateMutation.isPending || draft.draftBill.items.length === 0 || !draft.isDraftPaymentValid,
              icon: Save,
              onClick: draft.handleSaveEdit,
            }}
          />

          <CustomerModal
            open={draft.customerModalOpen}
            onClose={() => draft.setCustomerModalOpen(false)}
            onSaved={(customer) => {
              draft.setDraftCustomer(customer);
              draft.setCustomerModalOpen(false);
            }}
          />
        </div>
      ) : (
        <BillDetailReadView
          sale={sale}
          items={items}
          lineSort={lineSort}
          onLineSortChange={setLineSort}
          onReprint={handleReprint}
        />
      )}

      {/* Hidden Receipt Component */}
      {reprintSale && (
        <div className="print-only-container">
          <ReceiptPrinter
            sale={reprintSale.sale}
            items={reprintSale.items}
            billSettings={billSettings}
            generalSettings={generalSettings}
          />
        </div>
      )}
    </div>
  );
}
