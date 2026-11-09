import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  Printer,
  RotateCcw,
  AlertTriangle,
  Receipt,
  User,
  CreditCard,
  Calendar,
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
import { formatPaise, formatDateTime, getCurrencySymbol } from '../../../lib/formatters';
import { nextSortState, sortRows } from '../../../lib/sort';
import SortableTh from '../../../components/common/SortableTh';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import { computeBillTotals } from '../../../stores/pos.store';
import PosBillEditor from '../pos/PosBillEditor';
import AddCustomerModal from '../pos/AddCustomerModal';
import { useWorkspaceStore } from '../../../stores/workspaceStore';

export default function BillDetailView({ saleId, invoiceNumber, editRequest }) {
  const currencySymbol = getCurrencySymbol();
  const queryClient = useQueryClient();
  const { openOrFocus, closeTab, activeTabId } = useWorkspaceStore();

  const [reprintSale, setReprintSale] = useState(null);
  const [lineSort, setLineSort] = useState({ key: null, dir: null });
  const handledEditRequest = useRef(null);
  const searchInputRef = useRef(null);

  // ── Edit mode state ──
  const [editing, setEditing] = useState(false);
  const [editError, setEditError] = useState('');
  const [draftBill, setDraftBill] = useState(null);
  const [customerModalOpen, setCustomerModalOpen] = useState(false);
  const [editSearchQuery, setEditSearchQuery] = useState('');
  const [editSelectedCategory, setEditSelectedCategory] = useState(null);
  const [editSearchFocused, setEditSearchFocused] = useState(false);

  const lookupKey = saleId || invoiceNumber;

  const { data: saleDetails, isLoading, isError } = useQuery({
    queryKey: ['sale_details', lookupKey],
    queryFn: () => tauriApi.getSaleDetails(lookupKey),
    enabled: !!lookupKey,
  });

  const { data: billSettings } = useQuery({
    queryKey: ['bill_settings'],
    queryFn: () => tauriApi.getBillSettings(),
  });

  const { data: generalSettings } = useQuery({
    queryKey: ['general_settings'],
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

  const allowNegativeStock = generalSettings?.allow_negative_stock ?? false;

  const productStockById = useMemo(() => {
    const map = new Map();
    for (const p of catalogProducts) map.set(p.id, p);
    return map;
  }, [catalogProducts]);

  const stockAllowanceByProduct = useMemo(() => {
    const map = new Map();
    for (const it of saleDetails?.items || []) {
      map.set(it.product_id, (map.get(it.product_id) || 0) + it.quantity);
    }
    return map;
  }, [saleDetails]);

  const editProducts = useMemo(() => {
    const q = editSearchQuery.trim().toLowerCase();
    return catalogProducts.filter((p) => {
      if (editSelectedCategory && p.category_id !== editSelectedCategory) return false;
      if (!q) return true;
      return (
        p.name.toLowerCase().includes(q) ||
        (p.sku && p.sku.toLowerCase().includes(q))
      );
    });
  }, [catalogProducts, editSelectedCategory, editSearchQuery]);

  // Seed POS draft from loaded bill
  const seedDraft = useCallback(
    (details) => {
      if (!details) return;
      const { sale, items } = details;
      setDraftBill({
        items: items.map((it) => {
          const product = catalogProducts.find((p) => p.id === it.product_id);
          return {
            productId: it.product_id,
            name: it.product_name,
            sku: it.product_sku || product?.sku || '',
            unitPricePaise: it.unit_price_paise,
            mrpPaise: product?.mrp_paise ?? it.unit_price_paise,
            costPricePaise: product?.cost_price_paise ?? it.cost_price_paise ?? 0,
            taxRate: it.tax_rate || 0,
            quantity: it.quantity,
            discountPaise: it.discount_paise || 0,
          };
        }),
        customer:
          sale.customer_id || sale.customer_name
            ? {
                id: sale.customer_id || null,
                name: sale.customer_name || '',
                phone: sale.customer_phone || '',
                address: sale.customer_address || '',
              }
            : null,
        discountType: 'FIXED',
        discountValue: (sale.discount_paise || 0) / 100,
        paymentMethod: sale.payment_method || 'CASH',
        isFullPayment: sale.paid_paise === sale.total_paise,
        amountTenderedPaise: sale.paid_paise || 0,
        notes: sale.notes || '',
      });
    },
    [catalogProducts]
  );

  const enterEdit = () => {
    seedDraft(saleDetails);
    setEditError('');
    setEditSearchQuery('');
    setEditSelectedCategory(null);
    setEditing(true);
  };

  useEffect(() => {
    if (!editRequest || !saleDetails) return;
    if (handledEditRequest.current === editRequest) return;

    handledEditRequest.current = editRequest;
    seedDraft(saleDetails);
    setEditError('');
    setEditSearchQuery('');
    setEditSelectedCategory(null);
    setEditing(true);
  }, [editRequest, saleDetails, seedDraft]);

  useEffect(() => {
    if (!editing) return;
    const handleKeyDown = (e) => {
      const isCmd = e.ctrlKey || e.metaKey;
      if (isCmd && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        searchInputRef.current?.focus();
        searchInputRef.current?.select();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [editing]);

  const cancelEdit = () => {
    setEditing(false);
    setEditError('');
    setDraftBill(null);
    setCustomerModalOpen(false);
    setEditSearchQuery('');
    setEditSelectedCategory(null);
  };

  const draftTotals = useMemo(() => computeBillTotals(draftBill), [draftBill]);

  const addProductToDraft = (product) => {
    if (!allowNegativeStock) {
      const available =
        (productStockById.get(product.id)?.current_stock ?? product.current_stock ?? 0) +
        (stockAllowanceByProduct.get(product.id) || 0);
      const inCart = draftBill?.items.find((it) => it.productId === product.id)?.quantity || 0;

      if (available <= 0) {
        toast.warning(`"${product.name}" is out of stock.`);
        return;
      }
      if (inCart + 1 > available) {
        toast.warning(
          `Only ${available} unit${available === 1 ? '' : 's'} of "${product.name}" available in stock.`,
          { title: 'Stock limit reached' }
        );
        return;
      }
    }

    setDraftBill((prev) => {
      if (!prev) return prev;
      const existing = prev.items.find((it) => it.productId === product.id);
      const items = existing
        ? prev.items.map((it) =>
            it.productId === product.id ? { ...it, quantity: it.quantity + 1 } : it
          )
        : [
            ...prev.items,
            {
              productId: product.id,
              name: product.name,
              sku: product.sku || '',
              unitPricePaise: product.mrp_paise || 0,
              mrpPaise: product.mrp_paise || 0,
              costPricePaise: product.cost_price_paise || 0,
              taxRate: product.tax_rate || 0,
              quantity: 1,
              discountPaise: 0,
            },
          ];
      return { ...prev, items };
    });
  };

  const updateDraftItemQty = (productId, quantity) => {
    if (quantity <= 0) {
      setDraftBill((prev) =>
        prev ? { ...prev, items: prev.items.filter((it) => it.productId !== productId) } : prev
      );
      return;
    }

    if (!allowNegativeStock) {
      const available =
        (productStockById.get(productId)?.current_stock || 0) +
        (stockAllowanceByProduct.get(productId) || 0);
      if (quantity > available) {
        const name = draftBill?.items.find((it) => it.productId === productId)?.name || 'item';
        toast.warning(
          `Only ${available} unit${available === 1 ? '' : 's'} of "${name}" available in stock.`,
          { title: 'Stock limit reached' }
        );
        return;
      }
    }

    setDraftBill((prev) =>
      prev
        ? {
            ...prev,
            items: prev.items.map((it) =>
              it.productId === productId ? { ...it, quantity } : it
            ),
          }
        : prev
    );
  };

  const updateDraftItemPrice = (productId, unitPricePaise) => {
    setDraftBill((prev) =>
      prev
        ? {
            ...prev,
            items: prev.items.map((it) =>
              it.productId === productId
                ? { ...it, unitPricePaise: Math.max(0, unitPricePaise || 0) }
                : it
            ),
          }
        : prev
    );
  };

  const updateDraftItemDiscount = (productId, discountPaise) => {
    setDraftBill((prev) =>
      prev
        ? {
            ...prev,
            items: prev.items.map((it) =>
              it.productId === productId
                ? { ...it, discountPaise: Math.max(0, discountPaise || 0) }
                : it
            ),
          }
        : prev
    );
  };

  const removeDraftItem = (productId) => {
    setDraftBill((prev) =>
      prev ? { ...prev, items: prev.items.filter((it) => it.productId !== productId) } : prev
    );
  };

  const setDraftCustomer = (customer) => {
    setDraftBill((prev) => (prev ? { ...prev, customer } : prev));
  };

  const setDraftDiscount = (discountType, discountValue) => {
    setDraftBill((prev) =>
      prev
        ? {
            ...prev,
            discountType,
            discountValue: Math.max(0, Number(discountValue) || 0),
          }
        : prev
    );
  };

  const setDraftPaymentMethod = (paymentMethod) => {
    setDraftBill((prev) => (prev ? { ...prev, paymentMethod } : prev));
  };

  const setDraftFullPayment = (isFullPayment) => {
    setDraftBill((prev) => (prev ? { ...prev, isFullPayment } : prev));
  };

  const setDraftAmountTendered = (amountTenderedPaise) => {
    setDraftBill((prev) =>
      prev ? { ...prev, amountTenderedPaise: Math.max(0, amountTenderedPaise || 0) } : prev
    );
  };

  const setDraftNotes = (notes) => {
    setDraftBill((prev) => (prev ? { ...prev, notes } : prev));
  };

  // Mutations
  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }) => tauriApi.cancelSale(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sales'] });
      queryClient.invalidateQueries({ queryKey: ['sale_details'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      toast.success('Sale cancelled and items returned to stock');
    },
    onError: (err) => toast.error(`Failed to cancel sale: ${err}`, { title: 'Cancellation failed' }),
  });

  const updateMutation = useMutation({
    mutationFn: (payload) => tauriApi.updateSale(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sales'] });
      queryClient.invalidateQueries({ queryKey: ['sale_details', lookupKey] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      setEditing(false);
      setEditError('');
      setDraftBill(null);
      setCustomerModalOpen(false);
      setEditSearchQuery('');
      setEditSelectedCategory(null);
      toast.success('Bill amended and inventory adjustments logged');
    },
    onError: (err) => {
      const message = `Save failed: ${String(err)}`;
      setEditError(message);
      toast.error(message, { title: 'Update failed' });
    },
  });

  const handleSaveEdit = async () => {
    setEditError('');
    if (!draftBill || draftBill.items.length === 0) {
      const message = 'A bill must have at least one item.';
      setEditError(message);
      toast.warning(message);
      return;
    }

    const { confirmed, value: amendmentReason } = await confirmDialog({
      title: 'Confirm Bill Amendment',
      message: `Save changes to invoice #${saleDetails?.sale?.invoice_number}? A structured amendment record will be logged.`,
      input: {
        label: 'Amendment Reason (Audit Trail)',
        defaultValue: 'Correction of line item / rate amendment',
        placeholder: 'Reason for change',
        required: true,
      },
      confirmLabel: 'Save Changes',
      cancelLabel: 'Keep Editing',
      tone: 'primary',
      icon: Save,
    });

    if (!confirmed) return;

    const payload = {
      sale_id: saleDetails?.sale?.id || saleId,
      customer_id: draftBill.customer?.id || null,
      customer_name: draftBill.customer?.name?.trim() || null,
      customer_phone: draftBill.customer?.phone?.trim() || null,
      customer_address: draftBill.customer?.address?.trim() || null,
      items: draftBill.items.map((it) => ({
        product_id: it.productId,
        quantity: Number(it.quantity),
        unit_price_paise: Number(it.unitPricePaise),
        discount_paise: Number(it.discountPaise || 0),
      })),
      discount_paise: draftTotals.discountPaise,
      payment_method: draftBill.paymentMethod,
      paid_paise: draftTotals.tenderedPaise,
      notes: amendmentReason
        ? `[Amended: ${amendmentReason}] ${draftBill.notes || ''}`.trim()
        : draftBill.notes || null,
    };
    updateMutation.mutate(payload);
  };

  const handleReprint = () => {
    if (!saleDetails) return;
    setReprintSale(saleDetails);
    toast.info('Preparing receipt for printing');
    setTimeout(() => window.print(), 100);
  };

  const handleBack = () => {
    openOrFocus('Bill History', 'bills');
    if (activeTabId) closeTab(activeTabId);
  };

  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-canvas text-text-muted">
        <div className="flex items-center gap-3">
          <div className="w-5 h-5 border-2 border-brand border-t-transparent rounded-full animate-spin" />
          <span className="text-sm">Loading bill details…</span>
        </div>
      </div>
    );
  }

  if (isError || !saleDetails) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center bg-canvas text-text-secondary gap-3">
        <Receipt className="w-10 h-10 text-text-muted" />
        <p className="text-sm">Could not find bill details for ID: {saleId}</p>
        <button
          type="button"
          onClick={handleBack}
          className="px-3.5 py-1.5 rounded-lg bg-surface hover:bg-surface-secondary border border-border-default text-text-primary text-xs font-semibold cursor-pointer"
        >
          Return to Bill History
        </button>
      </div>
    );
  }

  const { sale, items } = saleDetails;
  const sortedItems = sortRows(items, lineSort, (row, key) => row[key]);
  const isCancelled = sale.status === 'CANCELLED';
  const isLegacy = sale.status === 'LEGACY';

  const handleCancelSale = async () => {
    const { confirmed, value } = await confirmDialog({
      title: `Cancel Bill #${sale.invoice_number}?`,
      message: `Are you sure you want to cancel invoice #${sale.invoice_number}? All items will be returned to inventory.`,
      input: {
        label: 'Cancellation Reason',
        defaultValue: 'Customer return / refund',
        placeholder: 'Reason for return',
        required: true,
      },
      confirmLabel: 'Cancel Bill',
      cancelLabel: 'Keep Active',
      tone: 'danger',
      icon: RotateCcw,
    });

    if (confirmed) {
      cancelMutation.mutate({
        id: sale.id,
        reason: value || 'Customer return / refund',
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
            onClick={editing ? cancelEdit : handleBack}
            className="p-1.5 rounded-lg bg-surface-secondary hover:bg-surface border border-border-default text-text-secondary hover:text-text-primary transition-colors cursor-pointer"
            title={editing ? 'Discard edit' : 'Back to Bill History'}
          >
            {editing ? <X className="w-4 h-4" /> : <ArrowLeft className="w-4 h-4" />}
          </button>
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
              <Receipt className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h2 className="text-sm font-bold text-text-primary font-mono tracking-wide">
                  Bill #{sale.invoice_number || invoiceNumber}
                </h2>
                {editing ? (
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border bg-warning-bg text-warning border-warning/30">
                    <Pencil className="w-3 h-3" /> Editing Mode
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
                      <History className="w-3 h-3" />
                    ) : (
                      <CheckCircle2 className="w-3 h-3" />
                    )}
                    {isLegacy ? 'Legacy' : sale.status}
                  </span>
                )}
              </div>
              <p className="text-[11px] text-text-muted font-mono">{formatDateTime(sale.created_at)}</p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {!isCancelled && !isLegacy && !editing && (
            <>
              <button
                type="button"
                onClick={enterEdit}
                className="px-3.5 py-1.5 rounded-lg text-text-secondary hover:text-text-primary bg-surface hover:bg-surface-secondary text-xs font-semibold flex items-center gap-1.5 transition-colors border border-border-default cursor-pointer shadow-2xs"
              >
                <Pencil className="w-3.5 h-3.5 text-text-muted" />
                Amend Bill
              </button>
              <button
                type="button"
                onClick={handleCancelSale}
                className="px-3.5 py-1.5 rounded-lg text-danger hover:bg-danger-bg text-xs font-semibold flex items-center gap-1.5 transition-colors border border-danger/30 cursor-pointer shadow-2xs"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                Cancel / Return
              </button>
            </>
          )}

          {editing && (
            <>
              <button
                type="button"
                onClick={cancelEdit}
                className="px-3.5 py-1.5 rounded-lg text-text-secondary hover:text-text-primary hover:bg-surface-secondary text-xs font-medium border border-border-default transition-colors cursor-pointer"
              >
                Discard
              </button>
              <button
                type="button"
                onClick={handleSaveEdit}
                disabled={updateMutation.isPending || !draftBill?.items.length}
                className="px-4 py-1.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-bold text-xs flex items-center gap-2 shadow-xs transition-colors disabled:opacity-50 cursor-pointer"
              >
                <Save className="w-3.5 h-3.5" />
                {updateMutation.isPending ? 'Saving…' : 'Save Amendment'}
              </button>
            </>
          )}

          {!editing && (
            <button
              type="button"
              onClick={handleReprint}
              className="px-4 py-1.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-bold text-xs flex items-center gap-2 shadow-xs transition-colors cursor-pointer"
            >
              <Printer className="w-3.5 h-3.5" />
              Reprint Receipt
            </button>
          )}
        </div>
      </div>

      {/* ── Content: POS Editor in Edit Mode vs Two-Column Layout in Read Mode ── */}
      {editing && draftBill ? (
        <div className="flex-1 flex flex-col overflow-hidden relative">
          {editError && (
            <div className="p-3 mx-4 mt-2 rounded-xl bg-danger-bg border border-danger/30 flex items-start gap-2.5 text-xs text-danger shrink-0">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{editError}</span>
            </div>
          )}

          <PosBillEditor
            bill={draftBill}
            totals={draftTotals}
            products={editProducts}
            categories={categories}
            customers={customers}
            stockProducts={catalogProducts}
            allowNegativeStock={allowNegativeStock}
            stockAllowanceByProduct={stockAllowanceByProduct}
            currencySymbol={currencySymbol}
            searchQuery={editSearchQuery}
            onSearchQueryChange={setEditSearchQuery}
            selectedCategory={editSelectedCategory}
            onSelectedCategoryChange={setEditSelectedCategory}
            searchFocused={editSearchFocused}
            onSearchFocusedChange={setEditSearchFocused}
            searchInputRef={searchInputRef}
            onAddItem={addProductToDraft}
            onUpdateItemQty={updateDraftItemQty}
            onUpdateItemPrice={updateDraftItemPrice}
            onUpdateItemDiscount={updateDraftItemDiscount}
            onRemoveItem={removeDraftItem}
            onSetCustomer={setDraftCustomer}
            onSetDiscount={setDraftDiscount}
            onSetPaymentMethod={setDraftPaymentMethod}
            onSetFullPayment={setDraftFullPayment}
            onSetAmountTendered={setDraftAmountTendered}
            onRequestAddCustomer={() => setCustomerModalOpen(true)}
            showNotes={true}
            notes={draftBill.notes || ''}
            onNotesChange={setDraftNotes}
            primaryAction={{
              label: 'Save Amendment',
              pendingLabel: 'Saving Changes…',
              pending: updateMutation.isPending,
              disabled: updateMutation.isPending || draftBill.items.length === 0,
              icon: Save,
              onClick: handleSaveEdit,
            }}
          />

          <AddCustomerModal
            open={customerModalOpen}
            onClose={() => setCustomerModalOpen(false)}
            onCreated={(customer) => {
              setDraftCustomer(customer);
              setCustomerModalOpen(false);
            }}
          />
        </div>
      ) : (
        /* ── READ MODE: Two-Column Workspace ── */
        <div className="flex-1 flex overflow-hidden">
          {/* Left Column: Line Items Table */}
          <div className="flex-1 flex flex-col p-6 overflow-y-auto space-y-4 min-w-0 bg-canvas">
            {isCancelled && (
              <div className="p-4 rounded-xl bg-danger-bg border border-danger/30 flex items-start gap-3 shrink-0">
                <AlertTriangle className="w-5 h-5 text-danger shrink-0 mt-0.5" />
                <div className="text-xs">
                  <h4 className="font-bold text-danger">This bill has been cancelled</h4>
                  <p className="text-text-secondary mt-0.5">
                    Items were returned to inventory and transaction recorded as voided.
                  </p>
                </div>
              </div>
            )}

            {/* Line Items Table Card */}
            <div className="rounded-xl border border-border-default overflow-hidden bg-surface shadow-xs flex-1 flex flex-col">
              <div className="px-4 py-3 border-b border-border-default bg-surface-secondary flex items-center justify-between shrink-0">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold uppercase tracking-wider text-text-primary">Itemized Bill</span>
                  <span className="text-[11px] font-mono px-2 py-0.5 rounded-full bg-surface border border-border-default text-text-muted">
                    {items.length} line{items.length !== 1 ? 's' : ''}
                  </span>
                </div>
                <span className="text-xs text-text-muted font-mono">
                  {items.reduce((acc, it) => acc + (it.quantity || 0), 0)} total units
                </span>
              </div>

              <div className="flex-1 overflow-x-auto overflow-y-auto">
                <table className="w-full text-left border-collapse text-xs select-text">
                  <thead className="bg-table-header-bg text-[11px] font-semibold text-table-header-text uppercase tracking-wider sticky top-0 border-b border-table-header-border z-10 shadow-2xs">
                    <tr>
                      <th className="py-2.5 px-4 w-12 text-center">#</th>
                      <SortableTh
                        sortKey="product_name"
                        sort={lineSort}
                        onSort={(key) => setLineSort((p) => nextSortState(p, key))}
                        className="py-2.5 px-4"
                      >
                        Product Name
                      </SortableTh>
                      <SortableTh
                        sortKey="unit_price_paise"
                        sort={lineSort}
                        onSort={(key) => setLineSort((p) => nextSortState(p, key))}
                        align="right"
                        className="py-2.5 px-4"
                      >
                        Unit Rate
                      </SortableTh>
                      <SortableTh
                        sortKey="quantity"
                        sort={lineSort}
                        onSort={(key) => setLineSort((p) => nextSortState(p, key))}
                        align="center"
                        className="py-2.5 px-4"
                      >
                        Quantity
                      </SortableTh>
                      <SortableTh
                        sortKey="discount_paise"
                        sort={lineSort}
                        onSort={(key) => setLineSort((p) => nextSortState(p, key))}
                        align="right"
                        className="py-2.5 px-4"
                      >
                        Discount
                      </SortableTh>
                      <SortableTh
                        sortKey="tax_rate"
                        sort={lineSort}
                        onSort={(key) => setLineSort((p) => nextSortState(p, key))}
                        align="right"
                        className="py-2.5 px-4"
                      >
                        Tax %
                      </SortableTh>
                      <SortableTh
                        sortKey="total_paise"
                        sort={lineSort}
                        onSort={(key) => setLineSort((p) => nextSortState(p, key))}
                        align="right"
                        className="py-2.5 px-4"
                      >
                        Total
                      </SortableTh>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border-default">
                    {sortedItems.map((it, idx) => (
                      <tr key={it.id || idx} className="hover:bg-surface-secondary/60 transition-colors">
                        <td className="py-3 px-4 text-text-muted font-mono text-center">{idx + 1}</td>
                        <td className="py-3 px-4">
                          <div className="font-semibold text-text-primary">{it.product_name}</div>
                          {it.product_sku && (
                            <div className="text-[10px] text-text-muted font-mono mt-0.5">{it.product_sku}</div>
                          )}
                        </td>
                        <td className="py-3 px-4 text-right financial-value text-text-secondary">
                          {formatPaise(it.unit_price_paise)}
                        </td>
                        <td className="py-3 px-4 text-center font-bold text-text-primary financial-value">
                          {it.quantity}
                        </td>
                        <td className="py-3 px-4 text-right financial-value text-amber-600">
                          {it.discount_paise > 0 ? `-${formatPaise(it.discount_paise)}` : '—'}
                        </td>
                        <td className="py-3 px-4 text-right font-mono text-text-muted">
                          {it.tax_rate != null ? `${it.tax_rate}%` : '0%'}
                        </td>
                        <td className="py-3 px-4 text-right font-bold text-text-primary financial-value">
                          {formatPaise(it.total_paise)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* Right Column: Metadata & Summary Sidebar */}
          <div className="w-80 lg:w-96 border-l border-border-default bg-surface flex flex-col overflow-y-auto p-5 space-y-4 shrink-0">
            {/* Customer Details Card */}
            <div className="p-3.5 rounded-xl bg-surface-secondary border border-border-default space-y-2">
              <div className="flex items-center gap-2 text-xs font-semibold text-text-primary">
                <User className="w-4 h-4 text-brand" />
                <span className="uppercase tracking-wider text-[11px]">Customer Details</span>
              </div>
              <div className="pt-2 border-t border-border-default space-y-1">
                <div className="text-sm font-bold text-text-primary">
                  {sale.customer_name || 'Walk-in Customer'}
                </div>
                {sale.customer_phone ? (
                  <div className="text-xs text-text-muted font-mono">{sale.customer_phone}</div>
                ) : (
                  <div className="text-xs text-text-muted italic">No phone provided</div>
                )}
                {sale.customer_address && (
                  <div className="text-xs text-text-secondary mt-1">{sale.customer_address}</div>
                )}
              </div>
            </div>

            {/* Payment & Transaction Card */}
            <div className="p-3.5 rounded-xl bg-surface-secondary border border-border-default space-y-2">
              <div className="flex items-center gap-2 text-xs font-semibold text-text-primary">
                <CreditCard className="w-4 h-4 text-brand" />
                <span className="uppercase tracking-wider text-[11px]">Payment & Transaction</span>
              </div>
              <div className="pt-2 border-t border-border-default space-y-2 text-xs">
                <div className="flex items-center justify-between">
                  <span className="text-text-muted">Payment Method</span>
                  <span className="text-xs font-bold px-2 py-0.5 rounded bg-surface border border-border-default text-text-primary uppercase tracking-wide">
                    {sale.payment_method}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-text-muted">Cashier / Operator</span>
                  <span className="font-medium text-text-primary">
                    {sale.biller_name || 'Counter Terminal'}
                  </span>
                </div>
                <div className="flex items-center justify-between pt-1 border-t border-border-default">
                  <span className="text-text-muted flex items-center gap-1.5">
                    <Calendar className="w-3.5 h-3.5" /> Date & Time
                  </span>
                  <span className="font-mono text-text-primary">
                    {formatDateTime(sale.created_at)}
                  </span>
                </div>
              </div>
            </div>

            {/* Financial Summary Card */}
            <div className="p-3.5 rounded-xl bg-surface-secondary border border-border-default space-y-3 flex-1 flex flex-col justify-between">
              <div className="space-y-2.5">
                <div className="flex items-center gap-2 text-xs font-semibold text-text-primary">
                  <Receipt className="w-4 h-4 text-brand" />
                  <span className="uppercase tracking-wider text-[11px]">Financial Summary</span>
                </div>

                <div className="space-y-1.5 pt-2 border-t border-border-default text-xs text-text-secondary">
                  <div className="flex justify-between">
                    <span className="text-text-muted">Subtotal</span>
                    <span className="financial-value font-medium text-text-primary">
                      {formatPaise(sale.subtotal_paise)}
                    </span>
                  </div>

                  {sale.discount_paise > 0 && (
                    <div className="flex justify-between text-amber-600">
                      <span>Discount</span>
                      <span className="financial-value font-medium">
                        -{formatPaise(sale.discount_paise)}
                      </span>
                    </div>
                  )}

                  {sale.tax_paise > 0 && (
                    <div className="flex justify-between text-text-muted">
                      <span>GST Tax</span>
                      <span className="financial-value font-medium text-text-primary">
                        {formatPaise(sale.tax_paise)}
                      </span>
                    </div>
                  )}

                  <div className="pt-2 border-t border-border-default flex justify-between items-baseline">
                    <span className="text-sm font-bold text-text-primary uppercase tracking-wide">Grand Total</span>
                    <span className="text-xl font-bold financial-value text-text-primary">
                      {formatPaise(sale.total_paise)}
                    </span>
                  </div>
                </div>

                {/* Paid & Settlement Details */}
                <div className="bg-surface rounded-lg p-2.5 border border-border-default space-y-1 text-xs">
                  <div className="flex justify-between text-text-muted">
                    <span>Paid Amount ({sale.payment_method})</span>
                    <span className="financial-value font-semibold text-text-primary">
                      {formatPaise(sale.paid_paise)}
                    </span>
                  </div>
                  {sale.paid_paise > sale.total_paise ? (
                    <div className="flex justify-between text-success font-semibold">
                      <span>Change Returned</span>
                      <span className="financial-value">{formatPaise(sale.paid_paise - sale.total_paise)}</span>
                    </div>
                  ) : sale.paid_paise < sale.total_paise ? (
                    <div className="flex justify-between text-danger font-semibold">
                      <span>Balance Due</span>
                      <span className="financial-value">{formatPaise(sale.total_paise - sale.paid_paise)}</span>
                    </div>
                  ) : (
                    <div className="flex justify-between text-text-muted text-[11px]">
                      <span>Balance Status</span>
                      <span>Settled</span>
                    </div>
                  )}
                </div>

                {/* Notes & Audit Reason */}
                {sale.notes && (
                  <div className="pt-2 border-t border-border-default text-xs">
                    <span className="text-[10px] uppercase font-bold text-text-muted block mb-0.5">
                      Transaction Notes:
                    </span>
                    <p className="bg-surface p-2 rounded-lg border border-border-default text-text-primary leading-relaxed">
                      {sale.notes}
                    </p>
                  </div>
                )}
              </div>

              {/* Sidebar Action Button */}
              <div className="pt-3 border-t border-border-default">
                <button
                  type="button"
                  onClick={handleReprint}
                  className="w-full py-2 rounded-xl bg-surface hover:bg-surface-secondary text-text-primary font-semibold text-xs flex items-center justify-center gap-2 border border-border-default transition-colors cursor-pointer shadow-2xs"
                >
                  <Printer className="w-3.5 h-3.5 text-text-muted" />
                  <span>Reprint Receipt</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Hidden Receipt Component */}
      {reprintSale && (
        <div className="hidden">
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
