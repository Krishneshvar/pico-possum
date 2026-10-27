import { useState, useEffect, useCallback } from 'react';
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
  Plus,
  Trash2,
  X,
  Search,
  Save,
  ChevronDown,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDateTime, getCurrencySymbol } from '../../../lib/formatters';
import { nextSortState, sortRows } from '../../../lib/sort';
import SortableTh from '../../../components/common/SortableTh';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import { useWorkspaceStore } from '../../../stores/workspaceStore';

// ── Utility ──────────────────────────────────────────────────────────────────
const paise = (v) => Math.round(parseFloat(v || 0) * 100);
const rupees = (v) => ((v || 0) / 100).toFixed(2);

// ── Product Search / Add Row ──────────────────────────────────────────────────
function ProductSearchInput({ onSelect, allowNegativeStock = false }) {
  const [query, setQuery] = useState('');
  const { data: products = [] } = useQuery({
    queryKey: ['products'],
    queryFn: () => tauriApi.getProducts(),
    staleTime: 30_000,
  });

  const filtered = query.trim().length >= 1
    ? products.filter((p) =>
        p.name.toLowerCase().includes(query.toLowerCase()) ||
        (p.sku && p.sku.toLowerCase().includes(query.toLowerCase()))
      ).slice(0, 8)
    : [];

  return (
    <div className="relative">
      <div className="flex items-center gap-2 bg-slate-950 border border-emerald-500/50 rounded-lg px-2 py-1.5 focus-within:border-emerald-400">
        <Search className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
        <input
          autoFocus
          type="text"
          placeholder="Search product by name or SKU…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="bg-transparent text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none w-full"
        />
      </div>
      {filtered.length > 0 && (
        <div className="absolute top-full mt-1 left-0 right-0 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl z-50 overflow-hidden max-h-52 overflow-y-auto">
          {filtered.map((p) => {
            const isOut = !allowNegativeStock && p.current_stock <= 0;
            return (
            <button
              key={p.id}
              onClick={() => {
                if (isOut) {
                  alert(`"${p.name}" is out of stock.`);
                  return;
                }
                onSelect(p);
                setQuery('');
              }}
              className={`w-full flex items-center justify-between px-3 py-2.5 hover:bg-slate-800 text-left group transition-colors ${
                isOut ? 'opacity-60' : ''
              }`}
            >
              <div>
                <div className="text-xs font-semibold text-slate-200 group-hover:text-white">{p.name}</div>
                {p.sku && <div className="text-[10px] text-slate-500 font-mono">{p.sku}</div>}
              </div>
              <div className="text-right shrink-0 ml-3">
                <div className="text-xs font-mono font-bold text-emerald-400">{formatPaise(p.mrp_paise)}</div>
                <div className={`text-[10px] ${isOut ? 'text-rose-400 font-semibold' : 'text-slate-500'}`}>
                  {isOut ? 'Out of stock' : `Stock: ${p.current_stock}`}
                </div>
              </div>
            </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ── Main View ─────────────────────────────────────────────────────────────────
export default function BillDetailView({ saleId, invoiceNumber }) {
  const currencySymbol = getCurrencySymbol();
  const queryClient = useQueryClient();
  const { openOrFocus, closeTab, activeTabId } = useWorkspaceStore();

  const [reprintSale, setReprintSale] = useState(null);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('Customer return / refund');
  const [lineSort, setLineSort] = useState({ key: null, dir: null });

  // ── Edit mode state ──
  const [editing, setEditing] = useState(false);
  const [editError, setEditError] = useState('');

  // Draft fields
  const [draftItems, setDraftItems] = useState([]);
  const [draftCustomerName, setDraftCustomerName] = useState('');
  const [draftCustomerPhone, setDraftCustomerPhone] = useState('');
  const [draftCustomerId, setDraftCustomerId] = useState(null);
  const [draftPaymentMethod, setDraftPaymentMethod] = useState('CASH');
  const [draftPaidRupees, setDraftPaidRupees] = useState('0');
  const [draftDiscountRupees, setDraftDiscountRupees] = useState('0');
  const [draftNotes, setDraftNotes] = useState('');
  const [addingProduct, setAddingProduct] = useState(false);

  // Resolve the lookup key — prefer the UUID sale ID, fall back to invoice number
  // (used when navigating from the Stock Ledger via an invoice reference link).
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

  const allowNegativeStock = generalSettings?.allow_negative_stock ?? false;
  const productById = useCallback(
    (productId) => catalogProducts.find((p) => p.id === productId),
    [catalogProducts]
  );

  // ── Seed draft from loaded data ──
  const seedDraft = useCallback((details) => {
    if (!details) return;
    const { sale, items } = details;
    setDraftItems(items.map((it) => ({
      _tempId: it.id,
      product_id: it.product_id,
      product_name: it.product_name,
      product_sku: it.product_sku,
      quantity: it.quantity,
      unit_price_paise: it.unit_price_paise,
      discount_paise: it.discount_paise || 0,
      tax_rate: it.tax_rate || 0,
    })));
    setDraftCustomerName(sale.customer_name || '');
    setDraftCustomerPhone(sale.customer_phone || '');
    setDraftCustomerId(sale.customer_id || null);
    setDraftPaymentMethod(sale.payment_method || 'CASH');
    setDraftPaidRupees(rupees(sale.paid_paise));
    setDraftDiscountRupees(rupees(sale.discount_paise));
    setDraftNotes(sale.notes || '');
  }, []);

  const enterEdit = () => {
    seedDraft(saleDetails);
    setEditError('');
    setEditing(true);
  };

  const cancelEdit = () => {
    setEditing(false);
    setEditError('');
    setAddingProduct(false);
  };

  // ── Draft calculations ──
  const draftSubtotal = draftItems.reduce((sum, it) => {
    return sum + (it.unit_price_paise * it.quantity) - (it.discount_paise || 0);
  }, 0);
  const draftTotalDiscount = paise(draftDiscountRupees);
  const draftTotal = Math.max(0, draftSubtotal - draftTotalDiscount);
  const draftPaid = paise(draftPaidRupees);
  const draftChange = Math.max(0, draftPaid - draftTotal);

  // ── Draft item helpers ──
  const updateDraftItem = (tempId, field, value) => {
    setDraftItems((prev) =>
      prev.map((it) => it._tempId === tempId ? { ...it, [field]: value } : it)
    );
  };

  const removeDraftItem = (tempId) => {
    setDraftItems((prev) => prev.filter((it) => it._tempId !== tempId));
  };

  const addProductToDraft = (product) => {
    if (!allowNegativeStock && product.current_stock <= 0) {
      alert(`"${product.name}" is out of stock.`);
      return;
    }

    const existing = draftItems.find((it) => it.product_id === product.id);
    if (existing) {
      setDraftItems((prev) =>
        prev.map((it) => it.product_id === product.id
          ? { ...it, quantity: it.quantity + 1 }
          : it
        )
      );
    } else {
      setDraftItems((prev) => [...prev, {
        _tempId: `new-${Date.now()}-${product.id}`,
        product_id: product.id,
        product_name: product.name,
        product_sku: product.sku,
        quantity: 1,
        unit_price_paise: product.mrp_paise,
        discount_paise: 0,
        tax_rate: product.tax_rate || 0,
      }]);
    }
    setAddingProduct(false);
  };

  // ── Mutations ──
  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }) => tauriApi.cancelSale(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sales'] });
      queryClient.invalidateQueries({ queryKey: ['sale_details', saleId] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      setCancelModalOpen(false);
    },
    onError: (err) => alert(`Failed to cancel sale: ${err}`),
  });

  const updateMutation = useMutation({
    mutationFn: (payload) => tauriApi.updateSale(payload),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['sales'] });
      queryClient.invalidateQueries({ queryKey: ['sale_details', saleId] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      setEditing(false);
      setEditError('');
    },
    onError: (err) => setEditError(`Save failed: ${String(err)}`),
  });

  const handleSaveEdit = () => {
    setEditError('');
    if (draftItems.length === 0) {
      setEditError('A bill must have at least one item. To remove all items, cancel the bill instead.');
      return;
    }
    const invalidItem = draftItems.find((it) => it.quantity <= 0 || it.unit_price_paise < 0);
    if (invalidItem) {
      setEditError('All items must have a quantity ≥ 1 and a valid price.');
      return;
    }

    if (!allowNegativeStock && saleDetails) {
      const oldQtyByProduct = new Map();
      for (const it of saleDetails.items) {
        oldQtyByProduct.set(
          it.product_id,
          (oldQtyByProduct.get(it.product_id) || 0) + it.quantity
        );
      }

      const newQtyByProduct = new Map();
      for (const it of draftItems) {
        newQtyByProduct.set(
          it.product_id,
          (newQtyByProduct.get(it.product_id) || 0) + Number(it.quantity)
        );
      }

      for (const [productId, newQty] of newQtyByProduct) {
        const oldQty = oldQtyByProduct.get(productId) || 0;
        const delta = newQty - oldQty;
        if (delta <= 0) continue;

        const product = productById(productId);
        if (product && delta > product.current_stock) {
          setEditError(
            `Insufficient stock for "${product.name}": available ${product.current_stock}, requested ${delta}.`
          );
          return;
        }
      }
    }

    const payload = {
      sale_id: saleId,
      customer_id: draftCustomerId,
      customer_name: draftCustomerName.trim() || null,
      customer_phone: draftCustomerPhone.trim() || null,
      customer_address: null,
      items: draftItems.map((it) => ({
        product_id: it.product_id,
        quantity: Number(it.quantity),
        unit_price_paise: Number(it.unit_price_paise),
        discount_paise: Number(it.discount_paise || 0),
      })),
      discount_paise: draftTotalDiscount,
      payment_method: draftPaymentMethod,
      paid_paise: draftPaid,
      notes: draftNotes.trim() || null,
    };
    updateMutation.mutate(payload);
  };

  const handleReprint = () => {
    if (!saleDetails) return;
    setReprintSale(saleDetails);
    setTimeout(() => window.print(), 100);
  };

  const handleBack = () => {
    openOrFocus('Bill History', 'bills');
    if (activeTabId) closeTab(activeTabId);
  };

  // ── Loading / Error states ──
  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-slate-950 text-slate-500">
        <div className="flex items-center gap-3">
          <div className="w-5 h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm">Loading bill details…</span>
        </div>
      </div>
    );
  }

  if (isError || !saleDetails) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center bg-slate-950 text-slate-400 gap-3">
        <Receipt className="w-10 h-10 text-slate-600" />
        <p className="text-sm">Could not find bill details for ID: {saleId}</p>
        <button onClick={handleBack} className="px-3.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold">
          Return to Bill History
        </button>
      </div>
    );
  }

  const { sale, items } = saleDetails;
  const sortedItems = sortRows(items, lineSort, (row, key) => row[key]);
  const isCancelled = sale.status === 'CANCELLED';

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* ── Top Bar ── */}
      <div className="h-14 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-4">
          <button
            onClick={editing ? cancelEdit : handleBack}
            className="p-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700 text-slate-400 hover:text-white transition-colors"
            title={editing ? 'Discard edit' : 'Back to Bill History'}
          >
            {editing ? <X className="w-4 h-4" /> : <ArrowLeft className="w-4 h-4" />}
          </button>
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
              <Receipt className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h2 className="text-sm font-bold text-slate-100 font-mono tracking-wide">
                  Bill #{sale.invoice_number || invoiceNumber}
                </h2>
                {editing ? (
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border bg-amber-500/15 text-amber-400 border-amber-500/30">
                    <Pencil className="w-3 h-3" /> Editing
                  </span>
                ) : (
                  <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border ${
                    isCancelled
                      ? 'bg-rose-500/15 text-rose-400 border-rose-500/30'
                      : 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
                  }`}>
                    {isCancelled ? <XCircle className="w-3 h-3" /> : <CheckCircle2 className="w-3 h-3" />}
                    {sale.status}
                  </span>
                )}
              </div>
              <p className="text-[11px] text-slate-500 font-mono">{formatDateTime(sale.created_at)}</p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {!isCancelled && !editing && (
            <>
              <button
                onClick={enterEdit}
                className="px-3.5 py-1.5 rounded-lg text-amber-400 hover:bg-amber-500/10 text-xs font-semibold flex items-center gap-1.5 transition-colors border border-amber-500/20 hover:border-amber-500/40"
              >
                <Pencil className="w-3.5 h-3.5" />
                Edit Bill
              </button>
              <button
                onClick={() => setCancelModalOpen(true)}
                className="px-3.5 py-1.5 rounded-lg text-rose-400 hover:bg-rose-500/10 text-xs font-semibold flex items-center gap-1.5 transition-colors border border-rose-500/20 hover:border-rose-500/40"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                Cancel / Return Sale
              </button>
            </>
          )}
          {editing && (
            <button
              onClick={handleSaveEdit}
              disabled={updateMutation.isPending}
              className="px-4 py-1.5 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-amber-500/20 transition-all disabled:opacity-60"
            >
              <Save className="w-3.5 h-3.5" />
              {updateMutation.isPending ? 'Saving…' : 'Save Changes'}
            </button>
          )}
          {!editing && (
            <button
              onClick={handleReprint}
              className="px-4 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 transition-all"
            >
              <Printer className="w-3.5 h-3.5" />
              Reprint Receipt
            </button>
          )}
        </div>
      </div>

      {/* ── Main Content ── */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {/* Error banner for edit */}
        {editError && (
          <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-2.5 text-xs text-rose-300">
            <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
            {editError}
          </div>
        )}

        {/* Cancelled Banner */}
        {isCancelled && !editing && (
          <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
            <div className="text-xs">
              <h4 className="font-bold text-rose-300">This sale has been voided / cancelled</h4>
              <p className="text-rose-400/80 mt-0.5">
                All inventory quantities have been restored to stock, and the invoice has been recorded as returned.
              </p>
            </div>
          </div>
        )}

        {/* ── READ MODE ── */}
        {!editing && (
          <>
            {/* Info Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
                  <User className="w-4 h-4 text-emerald-400" />
                  Customer Information
                </div>
                <div>
                  <div className="text-sm font-bold text-slate-200">{sale.customer_name || 'Walk-in Customer'}</div>
                  {sale.customer_phone
                    ? <div className="text-xs text-slate-400 font-mono mt-0.5">{sale.customer_phone}</div>
                    : <div className="text-xs text-slate-600 italic mt-0.5">No contact number provided</div>
                  }
                </div>
              </div>

              <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
                  <CreditCard className="w-4 h-4 text-cyan-400" />
                  Payment Details
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold px-2 py-0.5 rounded bg-slate-800 text-slate-200 uppercase">{sale.payment_method}</span>
                    <span className="text-xs text-slate-400">Paid: <strong className="text-slate-200 font-mono">{formatPaise(sale.paid_paise)}</strong></span>
                  </div>
                  <div className="text-[11px] text-slate-500 mt-1">
                    Biller: <span className="text-slate-400">{sale.biller_name || 'Counter Terminal'}</span>
                  </div>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
                  <Calendar className="w-4 h-4 text-amber-400" />
                  Transaction Timestamp
                </div>
                <div>
                  <div className="text-xs font-mono font-bold text-slate-200">{formatDateTime(sale.created_at)}</div>
                  <div className="text-[11px] text-slate-500 mt-1 font-mono">
                    Total Items: <span className="text-slate-300 font-bold">{items.length} item line{items.length !== 1 ? 's' : ''}</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Line Items Table */}
            <div className="rounded-xl border border-slate-800 overflow-hidden bg-slate-900/40">
              <div className="px-4 py-3 border-b border-slate-800 bg-slate-900/80 flex items-center justify-between">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-300">Itemized Line Items</h3>
                <span className="text-[11px] text-slate-500 font-mono">
                  {items.reduce((acc, it) => acc + (it.quantity || 0), 0)} total units
                </span>
              </div>

              <table className="w-full text-left border-collapse text-xs">
                <thead className="bg-slate-900/60 text-[11px] font-semibold text-slate-400 uppercase tracking-wider border-b border-slate-800">
                  <tr>
                    <th className="py-2.5 px-4 w-12 text-slate-500">#</th>
                    <SortableTh sortKey="product_name" sort={lineSort} onSort={(key) => setLineSort((p) => nextSortState(p, key))} className="py-2.5 px-4">Product Name</SortableTh>
                    <SortableTh sortKey="unit_price_paise" sort={lineSort} onSort={(key) => setLineSort((p) => nextSortState(p, key))} align="right" className="py-2.5 px-4">Unit Price</SortableTh>
                    <SortableTh sortKey="quantity" sort={lineSort} onSort={(key) => setLineSort((p) => nextSortState(p, key))} align="center" className="py-2.5 px-4">Qty</SortableTh>
                    <SortableTh sortKey="discount_paise" sort={lineSort} onSort={(key) => setLineSort((p) => nextSortState(p, key))} align="right" className="py-2.5 px-4">Discount</SortableTh>
                    <SortableTh sortKey="tax_rate" sort={lineSort} onSort={(key) => setLineSort((p) => nextSortState(p, key))} align="right" className="py-2.5 px-4">Tax Rate</SortableTh>
                    <SortableTh sortKey="total_paise" sort={lineSort} onSort={(key) => setLineSort((p) => nextSortState(p, key))} align="right" className="py-2.5 px-4">Total</SortableTh>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {sortedItems.map((it, idx) => (
                    <tr key={it.id || idx} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-3 px-4 text-slate-500 font-mono">{idx + 1}</td>
                      <td className="py-3 px-4">
                        <div className="font-semibold text-slate-200">{it.product_name}</div>
                        {it.product_sku && <div className="text-[10px] text-slate-500 font-mono">{it.product_sku}</div>}
                      </td>
                      <td className="py-3 px-4 text-right font-mono text-slate-300">{formatPaise(it.unit_price_paise)}</td>
                      <td className="py-3 px-4 text-center font-mono font-bold text-slate-200">{it.quantity}</td>
                      <td className="py-3 px-4 text-right font-mono text-amber-400">
                        {it.discount_paise > 0 ? `-${formatPaise(it.discount_paise)}` : '—'}
                      </td>
                      <td className="py-3 px-4 text-right font-mono text-slate-400">
                        {it.tax_rate != null ? `${it.tax_rate}%` : '0%'}
                      </td>
                      <td className="py-3 px-4 text-right font-mono font-bold text-slate-100">{formatPaise(it.total_paise)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="p-4 bg-slate-900/90 border-t border-slate-800 flex justify-end">
                <div className="w-72 space-y-2 text-xs">
                  <div className="flex justify-between text-slate-400">
                    <span>Subtotal</span>
                    <span className="font-mono text-slate-200">{formatPaise(sale.subtotal_paise)}</span>
                  </div>
                  {sale.discount_paise > 0 && (
                    <div className="flex justify-between text-amber-400 font-semibold">
                      <span>Overall Discount</span>
                      <span className="font-mono">-{formatPaise(sale.discount_paise)}</span>
                    </div>
                  )}
                  {sale.tax_paise > 0 && (
                    <div className="flex justify-between text-slate-400">
                      <span>Total Tax</span>
                      <span className="font-mono text-slate-200">{formatPaise(sale.tax_paise)}</span>
                    </div>
                  )}
                  <div className="pt-2 border-t border-slate-800 flex justify-between items-baseline">
                    <span className="text-sm font-bold text-slate-100">Final Bill Total</span>
                    <span className="text-lg font-mono font-bold text-emerald-400">{formatPaise(sale.total_paise)}</span>
                  </div>
                  <div className="flex justify-between text-[11px] text-slate-500 pt-1">
                    <span>Amount Paid ({sale.payment_method})</span>
                    <span className="font-mono text-slate-400">{formatPaise(sale.paid_paise)}</span>
                  </div>
                  {sale.notes && (
                    <div className="pt-2 border-t border-slate-800 text-slate-500 italic">
                      Notes: {sale.notes}
                    </div>
                  )}
                </div>
              </div>
            </div>
          </>
        )}

        {/* ── EDIT MODE ── */}
        {editing && (
          <div className="space-y-5">
            {/* Customer & Payment */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Customer */}
              <div className="p-4 rounded-xl bg-slate-900/60 border border-amber-500/20 space-y-3">
                <div className="flex items-center gap-2 text-xs font-semibold text-amber-400">
                  <User className="w-4 h-4" />
                  Customer (optional)
                </div>
                <div className="space-y-2">
                  <input
                    type="text"
                    value={draftCustomerName}
                    onChange={(e) => { setDraftCustomerName(e.target.value); setDraftCustomerId(null); }}
                    placeholder="Customer name…"
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-amber-500"
                  />
                  <input
                    type="text"
                    value={draftCustomerPhone}
                    onChange={(e) => setDraftCustomerPhone(e.target.value)}
                    placeholder="Phone number…"
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-amber-500"
                  />
                </div>
              </div>

              {/* Payment */}
              <div className="p-4 rounded-xl bg-slate-900/60 border border-amber-500/20 space-y-3">
                <div className="flex items-center gap-2 text-xs font-semibold text-amber-400">
                  <CreditCard className="w-4 h-4" />
                  Payment Details
                </div>
                <div className="space-y-2">
                  <div className="relative">
                    <select
                      value={draftPaymentMethod}
                      onChange={(e) => setDraftPaymentMethod(e.target.value)}
                      className="w-full appearance-none bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-amber-500 pr-7"
                    >
                      <option value="CASH">Cash</option>
                      <option value="UPI">UPI</option>
                      <option value="CARD">Card</option>
                    </select>
                    <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-500 pointer-events-none" />
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-400 shrink-0">Amount Paid ({currencySymbol})</span>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={draftPaidRupees}
                      onChange={(e) => setDraftPaidRupees(e.target.value)}
                      className="flex-1 bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-xs text-slate-200 font-mono focus:outline-none focus:border-amber-500"
                    />
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-400 shrink-0">Overall Discount ({currencySymbol})</span>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={draftDiscountRupees}
                      onChange={(e) => setDraftDiscountRupees(e.target.value)}
                      className="flex-1 bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-xs text-slate-200 font-mono focus:outline-none focus:border-amber-500"
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Line Items Editor */}
            <div className="rounded-xl border border-amber-500/20 overflow-hidden bg-slate-900/40">
              <div className="px-4 py-3 border-b border-amber-500/20 bg-slate-900/80 flex items-center justify-between">
                <h3 className="text-xs font-bold uppercase tracking-wider text-amber-400">
                  Edit Line Items
                </h3>
                <button
                  onClick={() => setAddingProduct((v) => !v)}
                  className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/20 transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Add Product
                </button>
              </div>

              {/* Product Search Input */}
              {addingProduct && (
                <div className="px-4 py-3 border-b border-slate-800 bg-slate-950/60">
                  <ProductSearchInput onSelect={addProductToDraft} allowNegativeStock={allowNegativeStock} />
                </div>
              )}

              {/* Items table */}
              <table className="w-full text-left border-collapse text-xs">
                <thead className="bg-slate-900/60 text-[11px] font-semibold text-slate-400 uppercase tracking-wider border-b border-slate-800">
                  <tr>
                    <th className="py-2.5 px-4 w-10"></th>
                    <th className="py-2.5 px-4">Product</th>
                    <th className="py-2.5 px-4 text-right">Unit Price ({currencySymbol})</th>
                    <th className="py-2.5 px-4 text-center">Qty</th>
                    <th className="py-2.5 px-4 text-right">Disc/item ({currencySymbol})</th>
                    <th className="py-2.5 px-4 text-right">Line Total</th>
                    <th className="py-2.5 px-4 w-10"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {draftItems.length === 0 && (
                    <tr>
                      <td colSpan={7} className="py-8 text-center text-slate-500 italic text-xs">
                        No items. Add a product above or this bill will not be saveable.
                      </td>
                    </tr>
                  )}
                  {draftItems.map((it) => {
                    const lineTotal = Math.max(0, (it.unit_price_paise * it.quantity) - (it.discount_paise || 0));
                    return (
                      <tr key={it._tempId} className="hover:bg-slate-800/20 transition-colors group">
                        <td className="py-2 px-4 text-slate-600 text-[10px] font-mono"></td>
                        <td className="py-2 px-4">
                          <div className="font-semibold text-slate-200">{it.product_name}</div>
                          {it.product_sku && <div className="text-[10px] text-slate-500 font-mono">{it.product_sku}</div>}
                        </td>
                        <td className="py-2 px-4">
                          <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={rupees(it.unit_price_paise)}
                            onChange={(e) => updateDraftItem(it._tempId, 'unit_price_paise', paise(e.target.value))}
                            className="w-24 ml-auto block bg-slate-950 border border-slate-700 rounded px-2 py-1 text-xs text-slate-200 font-mono focus:outline-none focus:border-amber-500 text-right"
                          />
                        </td>
                        <td className="py-2 px-4">
                          <input
                            type="number"
                            min="1"
                            step="1"
                            value={it.quantity}
                            onChange={(e) => updateDraftItem(it._tempId, 'quantity', Math.max(1, parseInt(e.target.value) || 1))}
                            className="w-16 mx-auto block bg-slate-950 border border-slate-700 rounded px-2 py-1 text-xs text-slate-200 font-mono focus:outline-none focus:border-amber-500 text-center"
                          />
                        </td>
                        <td className="py-2 px-4">
                          <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={rupees(it.discount_paise)}
                            onChange={(e) => updateDraftItem(it._tempId, 'discount_paise', paise(e.target.value))}
                            className="w-24 ml-auto block bg-slate-950 border border-slate-700 rounded px-2 py-1 text-xs text-slate-200 font-mono focus:outline-none focus:border-amber-500 text-right"
                          />
                        </td>
                        <td className="py-2 px-4 text-right font-mono font-bold text-slate-100">
                          {formatPaise(lineTotal)}
                        </td>
                        <td className="py-2 px-4 text-center">
                          <button
                            onClick={() => removeDraftItem(it._tempId)}
                            className="p-1 rounded hover:bg-rose-500/15 text-slate-600 hover:text-rose-400 transition-colors opacity-0 group-hover:opacity-100"
                            title="Remove item"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {/* Totals */}
              <div className="p-4 bg-slate-900/90 border-t border-amber-500/10 flex justify-end">
                <div className="w-72 space-y-2 text-xs">
                  <div className="flex justify-between text-slate-400">
                    <span>Subtotal</span>
                    <span className="font-mono text-slate-200">{formatPaise(draftSubtotal)}</span>
                  </div>
                  {draftTotalDiscount > 0 && (
                    <div className="flex justify-between text-amber-400 font-semibold">
                      <span>Overall Discount</span>
                      <span className="font-mono">-{formatPaise(draftTotalDiscount)}</span>
                    </div>
                  )}
                  <div className="pt-2 border-t border-slate-800 flex justify-between items-baseline">
                    <span className="text-sm font-bold text-slate-100">Bill Total</span>
                    <span className="text-lg font-mono font-bold text-amber-400">{formatPaise(draftTotal)}</span>
                  </div>
                  <div className="flex justify-between text-[11px] text-slate-400 pt-1">
                    <span>Amount Paid</span>
                    <span className="font-mono">{formatPaise(draftPaid)}</span>
                  </div>
                  {draftChange > 0 && (
                    <div className="flex justify-between text-[11px] text-emerald-400 font-semibold">
                      <span>Change</span>
                      <span className="font-mono">{formatPaise(draftChange)}</span>
                    </div>
                  )}
                  {draftPaid < draftTotal && (
                    <div className="flex justify-between text-[11px] text-rose-400 font-semibold">
                      <span>Balance Due</span>
                      <span className="font-mono">{formatPaise(draftTotal - draftPaid)}</span>
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Notes */}
            <div>
              <label className="text-xs text-slate-400 block mb-1.5">Bill Notes (optional)</label>
              <textarea
                value={draftNotes}
                onChange={(e) => setDraftNotes(e.target.value)}
                rows={2}
                placeholder="Internal notes or remarks…"
                className="w-full bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-amber-500 resize-none"
              />
            </div>

            {/* Info box */}
            <div className="p-3 rounded-xl bg-amber-500/5 border border-amber-500/20 text-xs text-amber-300/80 flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
              <div>
                <strong className="text-amber-300">Stock impact of editing:</strong> If you increase or add item quantities, stock will be automatically decreased. If you reduce or remove items, stock will be restored. All changes are logged in the audit trail and stock activity.
              </div>
            </div>

            {/* Save / Discard */}
            <div className="flex justify-end gap-3">
              <button
                onClick={cancelEdit}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:bg-slate-800 transition-colors border border-slate-700"
              >
                Discard Changes
              </button>
              <button
                onClick={handleSaveEdit}
                disabled={updateMutation.isPending}
                className="px-5 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-amber-500/20 transition-all disabled:opacity-60"
              >
                <Save className="w-3.5 h-3.5" />
                {updateMutation.isPending ? 'Saving Changes…' : 'Save Changes'}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Hidden Receipt Component for browser printing */}
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

      {/* Cancellation Reason Modal */}
      {cancelModalOpen && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex items-center gap-3 text-rose-400">
              <AlertTriangle className="w-6 h-6 shrink-0" />
              <h3 className="font-bold text-sm text-slate-100">Cancel &amp; Return Sale</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">
              This will mark Bill #{sale.invoice_number} as CANCELLED and immediately restore all item quantities back to inventory.
            </p>
            <div>
              <label className="text-xs text-slate-400 block mb-1">Reason for Return</label>
              <input
                type="text"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-xs text-slate-200 focus:outline-none focus:border-rose-500"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
              <button
                onClick={() => setCancelModalOpen(false)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800"
              >
                Back
              </button>
              <button
                onClick={() => cancelMutation.mutate({ id: saleId, reason: cancelReason })}
                disabled={cancelMutation.isPending}
                className="px-4 py-1.5 rounded-lg text-xs font-bold bg-rose-500 hover:bg-rose-400 text-white shadow-md shadow-rose-500/20 disabled:opacity-60"
              >
                Confirm Cancellation
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
