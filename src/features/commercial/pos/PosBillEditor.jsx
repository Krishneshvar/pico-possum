import { useState, useMemo } from 'react';
import {
  Search,
  Plus,
  Minus,
  Trash2,
  User,
  CreditCard,
  Banknote,
  QrCode,
  Gift,
  X,
  Receipt,
  RotateCcw,
  SlidersHorizontal,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { formatPaise, rupeesToPaise } from '../../../lib/formatters';
import Select from '../../../components/common/Select';

const EMPTY_STOCK_ALLOWANCE = new Map();

/**
 * Shared POS bill workspace used both for checkout (PosView) and historical bill amendments.
 * Standardized to 5 primary cart columns, natural checkout sidebar ordering,
 * solid neutral surfaces, and brand-blue interactions.
 */
export default function PosBillEditor({
  bill,
  totals,
  products = [],
  categories = [],
  customers = [],
  stockProducts = [],
  allowNegativeStock = false,
  stockAllowanceByProduct = EMPTY_STOCK_ALLOWANCE,
  currencySymbol = '₹',
  searchQuery,
  onSearchQueryChange,
  selectedCategory,
  onSelectedCategoryChange,
  searchFocused,
  onSearchFocusedChange,
  searchInputRef,
  onAddItem,
  onUpdateItemQty,
  onUpdateItemPrice,
  onUpdateItemDiscount,
  onRemoveItem,
  onSetCustomer,
  onSetDiscount,
  onSetPaymentMethod,
  onSetFullPayment,
  onSetAmountTendered,
  onRequestAddCustomer,
  primaryAction,
  showNotes = false,
  notes = '',
  onNotesChange,
}) {
  const [selectedProductId, setSelectedProductId] = useState(null);
  const [expandedRowId, setExpandedRowId] = useState(null);
  const [lastRemovedItem, setLastRemovedItem] = useState(null);
  const [cardRefNumber, setCardRefNumber] = useState('');
  const [upiRefNumber, setUpiRefNumber] = useState('');

  const stockById = useMemo(() => {
    const map = new Map();
    for (const p of stockProducts) map.set(p.id, p);
    return map;
  }, [stockProducts]);

  const availableStockFor = (productId) => {
    const product = stockById.get(productId);
    if (!product) return undefined;
    return (product.current_stock || 0) + (stockAllowanceByProduct.get(productId) || 0);
  };

  const filteredSearchProducts = products.slice(0, 8);
  const PrimaryIcon = primaryAction?.icon;

  const handleRemoveWithUndo = (item) => {
    setLastRemovedItem({ ...item });
    onRemoveItem(item.productId);
  };

  const handleUndoRemove = () => {
    if (!lastRemovedItem) return;
    const prod = stockById.get(lastRemovedItem.productId) || {
      id: lastRemovedItem.productId,
      name: lastRemovedItem.name,
      sku: lastRemovedItem.sku,
      mrp_paise: lastRemovedItem.mrpPaise,
      cost_price_paise: lastRemovedItem.costPricePaise,
      tax_rate: lastRemovedItem.taxRate,
    };
    onAddItem(prod);
    setLastRemovedItem(null);
  };

  return (
    <div className="flex-1 flex overflow-hidden bg-canvas">
      {/* ── Left Area: Search + Cart Table (~66% width) ── */}
      <div className="flex-1 flex flex-col border-r border-border-default overflow-hidden bg-surface">
        {/* Dominant Product & Barcode Search Bar */}
        <div className="p-3 bg-surface border-b border-border-default relative shrink-0">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted" />
            <input
              ref={searchInputRef}
              type="text"
              placeholder="Scan barcode, SKU, or search products... (Ctrl+K)"
              value={searchQuery}
              onChange={(e) => onSearchQueryChange(e.target.value)}
              onFocus={() => onSearchFocusedChange(true)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && filteredSearchProducts.length > 0) {
                  const exactMatch = filteredSearchProducts.find(
                    (p) =>
                      (p.sku && p.sku.toLowerCase() === searchQuery.trim().toLowerCase()) ||
                      p.name.toLowerCase() === searchQuery.trim().toLowerCase()
                  );
                  onAddItem(exactMatch || filteredSearchProducts[0]);
                  onSearchQueryChange('');
                  onSearchFocusedChange(false);
                  setTimeout(() => searchInputRef?.current?.focus(), 40);
                }
              }}
              className="w-full pl-10 pr-24 py-2.5 rounded-xl bg-surface-secondary border border-border-strong text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-text-muted font-mono bg-surface px-1.5 py-0.5 rounded border border-border-default">
              ↵ Enter
            </span>
          </div>

          {/* Autocomplete Dropdown */}
          {searchFocused && searchQuery && (
            <div className="absolute left-3 right-3 top-full mt-1 bg-surface border border-border-default rounded-xl shadow-xl z-50 overflow-hidden divide-y divide-border-default animate-scale-in">
              {filteredSearchProducts.length > 0 ? (
                filteredSearchProducts.map((p) => {
                  const available = availableStockFor(p.id) ?? p.current_stock;
                  const isOut = !allowNegativeStock && available <= 0;
                  const isLow = !allowNegativeStock && !isOut && available <= (p.min_stock_alert ?? 5);

                  return (
                    <button
                      key={p.id}
                      type="button"
                      onClick={() => {
                        onAddItem(p);
                        onSearchQueryChange('');
                        onSearchFocusedChange(false);
                        setTimeout(() => searchInputRef?.current?.focus(), 40);
                      }}
                      className={`w-full flex items-center justify-between px-4 py-2.5 text-left hover:bg-surface-secondary transition-colors cursor-pointer group ${
                        isOut ? 'opacity-50' : ''
                      }`}
                    >
                      <div>
                        <div className="text-xs font-semibold text-text-primary group-hover:text-brand">
                          {p.name}
                        </div>
                        <div className="text-[11px] text-text-muted flex items-center gap-2 mt-0.5">
                          <span className="font-mono">SKU: {p.sku || '-'}</span>
                          <span>•</span>
                          <span
                            className={
                              isOut
                                ? 'text-danger font-semibold'
                                : isLow
                                ? 'text-warning font-semibold'
                                : ''
                            }
                          >
                            {isOut ? 'Out of stock' : `Stock: ${available}`}
                          </span>
                        </div>
                      </div>
                      <div className="text-xs font-bold text-text-primary financial-value">
                        {formatPaise(p.mrp_paise)}
                      </div>
                    </button>
                  );
                })
              ) : (
                <div className="p-4 text-center text-xs text-text-muted">
                  No products matching "{searchQuery}"
                </div>
              )}
            </div>
          )}

          {/* Category Filter Pills */}
          <div className="flex items-center gap-1.5 mt-2.5 overflow-x-auto pb-0.5 text-xs">
            <button
              type="button"
              onClick={() => onSelectedCategoryChange(null)}
              className={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer shrink-0 ${
                selectedCategory === null
                  ? 'bg-brand text-white shadow-2xs'
                  : 'bg-surface-secondary text-text-secondary hover:text-text-primary border border-border-default'
              }`}
            >
              All Items
            </button>
            {categories.map((c) => (
              <button
                key={c.id}
                type="button"
                onClick={() => onSelectedCategoryChange(c.id === selectedCategory ? null : c.id)}
                className={`px-2.5 py-1 rounded-lg font-medium whitespace-nowrap transition-colors cursor-pointer shrink-0 ${
                  selectedCategory === c.id
                    ? 'bg-brand text-white shadow-2xs'
                    : 'bg-surface-secondary text-text-secondary hover:text-text-primary border border-border-default'
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
        </div>

        {/* Undo Toast Banner if item was removed */}
        {lastRemovedItem && (
          <div className="bg-brand-subtle border-b border-brand/20 px-4 py-2 flex items-center justify-between text-xs text-brand animate-fade-in">
            <span>Removed "{lastRemovedItem.name}" from cart.</span>
            <button
              type="button"
              onClick={handleUndoRemove}
              className="flex items-center gap-1 font-bold underline hover:opacity-80 cursor-pointer"
            >
              <RotateCcw className="w-3 h-3" />
              <span>Undo</span>
            </button>
          </div>
        )}

        {/* Cart Items Table: Standardized to 5 columns with expandable row for advanced edits */}
        <div className="flex-1 overflow-y-auto p-3">
          {bill.items.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-text-muted p-8">
              <div className="w-14 h-14 rounded-2xl bg-surface-secondary flex items-center justify-center mb-3 text-text-muted border border-border-default shadow-xs">
                <Receipt className="w-7 h-7" />
              </div>
              <h3 className="text-sm font-bold text-text-primary">Cart is empty</h3>
              <p className="text-xs text-text-muted mt-1 max-w-xs text-center leading-relaxed">
                Scan a barcode or press <kbd className="text-text-primary bg-surface-secondary border border-border-default px-1.5 py-0.5 rounded font-mono text-[11px]">Ctrl+K</kbd> to search and add items.
              </p>
            </div>
          ) : (
            <div className="rounded-xl border border-border-default overflow-hidden bg-surface shadow-2xs">
              <table className="w-full text-left border-collapse select-text">
                <thead className="bg-table-header-bg text-[11px] font-semibold text-table-header-text uppercase tracking-wider sticky top-0 border-b border-table-header-border z-10 shadow-2xs">
                  <tr className="h-9">
                    <th className="py-2 pl-3.5 pr-3 w-10 text-center">#</th>
                    <th className="py-2 px-3">Product</th>
                    <th className="py-2 px-3 w-28 text-right">Unit Price</th>
                    <th className="py-2 px-3 w-32 text-center">Quantity</th>
                    <th className="py-2 px-3 w-28 text-right">Line Total</th>
                    <th className="py-2 pl-2 pr-3.5 w-16 text-center">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-default text-xs">
                {bill.items.map((item, index) => {
                  const lineTotal = item.unitPricePaise * item.quantity - (item.discountPaise || 0);
                  const availableStock = availableStockFor(item.productId);
                  const exceedsStock =
                    !allowNegativeStock && availableStock != null && item.quantity > availableStock;
                  const isSelected = selectedProductId === item.productId;
                  const isExpanded = expandedRowId === item.productId;

                  return (
                    <tr
                      key={item.productId}
                      onClick={() => setSelectedProductId(item.productId)}
                      className={`transition-colors group ${
                        isSelected
                          ? 'bg-brand-subtle/50'
                          : 'hover:bg-surface-secondary/60'
                      }`}
                    >
                      <td className="py-2 px-3 text-center text-text-muted font-mono text-[11px]">
                        {index + 1}
                      </td>
                      <td className="py-2 px-3 font-medium text-text-primary">
                        <div className="font-semibold text-text-primary leading-tight">{item.name}</div>
                        <div className="text-[11px] text-text-muted font-mono flex items-center gap-2 mt-0.5">
                          <span>SKU: {item.sku || '—'}</span>
                          {item.discountPaise > 0 && (
                            <span className="text-amber-600 font-sans">
                              Disc: -{formatPaise(item.discountPaise)}
                            </span>
                          )}
                          {exceedsStock && (
                            <span className="text-danger font-semibold">
                              Only {availableStock} in stock
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-2 px-3 text-right">
                        <span className="financial-value text-text-secondary font-medium">
                          {formatPaise(item.unitPricePaise)}
                        </span>
                      </td>
                      <td className="py-2 px-3">
                        <div className="flex items-center justify-center gap-1 bg-surface-secondary border border-border-strong rounded-lg p-0.5 max-w-[100px] mx-auto">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              onUpdateItemQty(item.productId, item.quantity - 1);
                            }}
                            className="w-6 h-6 rounded flex items-center justify-center text-text-secondary hover:text-text-primary hover:bg-surface transition-colors cursor-pointer"
                          >
                            <Minus className="w-3 h-3" />
                          </button>
                          <input
                            type="number"
                            min="1"
                            value={item.quantity}
                            onClick={(e) => e.stopPropagation()}
                            onChange={(e) => {
                              const val = parseInt(e.target.value, 10);
                              onUpdateItemQty(item.productId, isNaN(val) ? 1 : val);
                            }}
                            className="w-9 text-center bg-transparent font-semibold text-xs text-text-primary focus:outline-none financial-value"
                          />
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              onUpdateItemQty(item.productId, item.quantity + 1);
                            }}
                            className="w-6 h-6 rounded flex items-center justify-center text-text-secondary hover:text-text-primary hover:bg-surface transition-colors cursor-pointer"
                          >
                            <Plus className="w-3 h-3" />
                          </button>
                        </div>
                      </td>
                      <td className="py-2 px-3 text-right">
                        <span className="financial-value font-bold text-text-primary text-[13px]">
                          {formatPaise(lineTotal)}
                        </span>
                      </td>
                      <td className="py-2 px-2 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              setExpandedRowId(isExpanded ? null : item.productId);
                            }}
                            title="Edit price/discount"
                            className={`p-1 rounded transition-colors cursor-pointer ${
                              isExpanded
                                ? 'text-brand bg-brand-subtle'
                                : 'text-text-muted hover:text-text-primary hover:bg-surface-secondary'
                            }`}
                          >
                            <SlidersHorizontal className="w-3.5 h-3.5" />
                          </button>
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleRemoveWithUndo(item);
                            }}
                            className="p-1 text-text-muted hover:text-danger hover:bg-danger-bg rounded transition-colors cursor-pointer"
                            title="Remove item"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          )}

          {/* Row Edit Drawer if a row is expanded */}
          {expandedRowId && (
            <div className="mt-3 p-3 bg-surface-secondary rounded-xl border border-border-default flex items-center gap-4 text-xs shadow-2xs">
              <span className="font-semibold text-text-primary">
                Edit Line: {bill.items.find((it) => it.productId === expandedRowId)?.name}
              </span>
              <div className="flex items-center gap-2">
                <span className="text-text-muted">Unit Rate (₹):</span>
                <input
                  type="number"
                  step="0.01"
                  value={(
                    (bill.items.find((it) => it.productId === expandedRowId)?.unitPricePaise || 0) / 100
                  ).toFixed(2)}
                  onChange={(e) =>
                    onUpdateItemPrice(expandedRowId, rupeesToPaise(e.target.value))
                  }
                  className="w-20 px-2 py-1 rounded border border-border-strong bg-surface text-text-primary font-mono text-xs focus:border-brand focus:outline-none"
                />
              </div>
              <div className="flex items-center gap-2">
                <span className="text-text-muted">Line Disc (₹):</span>
                <input
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  value={
                    (bill.items.find((it) => it.productId === expandedRowId)?.discountPaise || 0) / 100 || ''
                  }
                  onChange={(e) =>
                    onUpdateItemDiscount(expandedRowId, rupeesToPaise(e.target.value))
                  }
                  className="w-20 px-2 py-1 rounded border border-border-strong bg-surface text-text-primary font-mono text-xs focus:border-brand focus:outline-none"
                />
              </div>
              <button
                type="button"
                onClick={() => setExpandedRowId(null)}
                className="ml-auto text-xs font-semibold text-text-muted hover:text-text-primary cursor-pointer px-2 py-1 rounded bg-surface border border-border-default"
              >
                Close
              </button>
            </div>
          )}
        </div>

        {/* Persistent Cart Summary Footer */}
        <div className="h-10 bg-surface-secondary border-t border-border-default px-4 flex items-center justify-between text-xs text-text-secondary font-medium shrink-0">
          <div className="flex items-center gap-4">
            <span>
              Lines: <strong className="text-text-primary font-semibold">{bill.items.length}</strong>
            </span>
            <span>
              Total Units: <strong className="text-text-primary font-semibold">{totals.totalQty}</strong>
            </span>
          </div>
          <div>
            <span>
              Cart Subtotal:{' '}
              <strong className="text-text-primary font-bold financial-value text-sm ml-1">
                {formatPaise(totals.subtotalPaise)}
              </strong>
            </span>
          </div>
        </div>
      </div>

      {/* ── Right Checkout Sidebar (32–35%, min 360px) ── */}
      <div className="w-[360px] md:w-[380px] bg-canvas flex flex-col justify-between overflow-y-auto p-4 space-y-4 shrink-0">
        <div className="space-y-3.5">
          {/* 1. Customer Selector */}
          <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-text-primary flex items-center gap-1.5">
                <User className="w-3.5 h-3.5 text-brand" /> Customer
              </span>
              <button
                type="button"
                onClick={onRequestAddCustomer}
                className="text-[11px] font-semibold text-brand hover:underline cursor-pointer"
              >
                {bill.customer ? 'Change' : '+ Add New'}
              </button>
            </div>

            {bill.customer ? (
              <div className="flex items-center justify-between bg-surface-secondary p-2 rounded-lg border border-border-default text-xs">
                <div>
                  <div className="font-semibold text-text-primary">{bill.customer.name}</div>
                  <div className="text-text-muted text-[11px] font-mono">{bill.customer.phone || 'No phone'}</div>
                </div>
                <button
                  type="button"
                  onClick={() => onSetCustomer(null)}
                  className="text-text-muted hover:text-danger cursor-pointer p-1"
                  title="Remove customer"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            ) : (
              <Select
                value={bill.customer?.id || ''}
                onChange={(val) => {
                  const c = customers.find((cust) => String(cust.id) === String(val));
                  onSetCustomer(c || null);
                }}
                placeholder="Walk-in Customer"
                options={[
                  { value: '', label: 'Walk-in Customer' },
                  ...customers.map((c) => ({
                    value: String(c.id),
                    label: `${c.name}${c.phone ? ` (${c.phone})` : ''}`,
                  })),
                ]}
              />
            )}
          </div>

          {/* 2. Bill-Level Discount */}
          <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-text-primary">Bill Discount</span>
              <div className="flex bg-surface-secondary rounded-lg p-0.5 border border-border-default">
                <button
                  type="button"
                  onClick={() => onSetDiscount('FIXED', bill.discountValue)}
                  className={`px-2 py-0.5 rounded text-[11px] font-bold cursor-pointer transition-colors ${
                    bill.discountType === 'FIXED'
                      ? 'bg-brand text-white shadow-2xs'
                      : 'text-text-secondary hover:text-text-primary'
                  }`}
                >
                  {currencySymbol} Flat
                </button>
                <button
                  type="button"
                  onClick={() => onSetDiscount('PERCENT', bill.discountValue)}
                  className={`px-2 py-0.5 rounded text-[11px] font-bold cursor-pointer transition-colors ${
                    bill.discountType === 'PERCENT'
                      ? 'bg-brand text-white shadow-2xs'
                      : 'text-text-secondary hover:text-text-primary'
                  }`}
                >
                  %
                </button>
              </div>
            </div>
            <input
              type="number"
              min="0"
              step="0.01"
              placeholder="0.00"
              value={bill.discountValue || ''}
              onChange={(e) => onSetDiscount(bill.discountType, e.target.value)}
              className="w-full bg-surface-secondary border border-border-strong rounded-lg px-3 py-1.5 text-xs financial-value text-text-primary focus:outline-none focus:border-brand"
            />
          </div>

          {/* 3. Subtotal and Tax Breakdown */}
          <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-1.5 text-xs text-text-secondary">
            <div className="flex justify-between">
              <span>Subtotal</span>
              <span className="financial-value font-medium text-text-primary">
                {formatPaise(totals.subtotalPaise)}
              </span>
            </div>
            {totals.discountPaise > 0 && (
              <div className="flex justify-between text-amber-600">
                <span>Discount</span>
                <span className="financial-value font-medium">
                  -{formatPaise(totals.discountPaise)}
                </span>
              </div>
            )}
            {totals.taxPaise > 0 && (
              <div className="flex justify-between text-text-muted">
                <span>GST Tax</span>
                <span className="financial-value font-medium">
                  {formatPaise(totals.taxPaise)}
                </span>
              </div>
            )}
          </div>

          {/* 4. Grand Total (High Contrast, Neutral, 26–30px Bold, NOT Green) */}
          <div className="p-3.5 rounded-xl border border-border-strong bg-surface text-center shadow-xs">
            <div className="text-[11px] font-bold text-text-muted uppercase tracking-wider">
              Grand Total
            </div>
            <div className="text-3xl font-extrabold text-text-primary financial-value mt-0.5">
              {formatPaise(totals.totalPaise)}
            </div>
          </div>

          {/* 5. Payment Method Selector: Neutral Styling, Brand Blue for Selected */}
          <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2">
            <label className="text-xs font-semibold text-text-primary block">Payment Method</label>
            <div className="grid grid-cols-2 gap-2">
              {[
                { id: 'CASH', label: 'Cash', icon: Banknote },
                { id: 'UPI', label: 'UPI / QR', icon: QrCode },
                { id: 'CARD', label: 'Card', icon: CreditCard },
                { id: 'GIFT_CARD', label: 'Gift Card', icon: Gift },
              ].map((pm) => {
                const Icon = pm.icon;
                const isSelected = bill.paymentMethod === pm.id;
                return (
                  <button
                    key={pm.id}
                    type="button"
                    onClick={() => onSetPaymentMethod(pm.id)}
                    className={`flex items-center gap-2 p-2 rounded-lg text-xs font-medium border transition-colors cursor-pointer ${
                      isSelected
                        ? 'bg-brand text-white border-brand shadow-xs'
                        : 'bg-surface-secondary text-text-secondary border-border-default hover:text-text-primary hover:border-border-strong'
                    }`}
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    <span>{pm.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 6. Adaptive Payment Details (Conditioned on Payment Method) */}
          {bill.paymentMethod === 'CASH' && (
            <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2 animate-fade-in">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-text-primary">Amount Tendered</span>
                <button
                  type="button"
                  onClick={() => {
                    const next = !bill.isFullPayment;
                    onSetFullPayment(next);
                    if (next) onSetAmountTendered(totals.totalPaise);
                  }}
                  className={`text-[11px] px-2 py-0.5 rounded font-medium border cursor-pointer ${
                    bill.isFullPayment
                      ? 'bg-brand-subtle text-brand border-brand/30'
                      : 'bg-surface-secondary text-text-secondary border-border-default'
                  }`}
                >
                  {bill.isFullPayment ? 'Full Payment' : 'Custom Amount'}
                </button>
              </div>

              <input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={(totals.tenderedPaise / 100).toFixed(2)}
                disabled={bill.isFullPayment}
                onChange={(e) => onSetAmountTendered(rupeesToPaise(e.target.value))}
                className={`w-full bg-surface-secondary border border-border-strong rounded-lg px-3 py-1.5 text-base financial-value text-text-primary focus:outline-none focus:border-brand ${
                  bill.isFullPayment ? 'opacity-80' : ''
                }`}
              />

              {/* Quick Cash Denominations */}
              <div className="flex flex-wrap gap-1.5 pt-1">
                <button
                  type="button"
                  onClick={() => {
                    onSetFullPayment(true);
                    onSetAmountTendered(totals.totalPaise);
                  }}
                  className="px-2 py-1 rounded bg-surface-secondary hover:bg-surface text-text-primary text-[11px] font-bold border border-border-default transition-colors cursor-pointer"
                >
                  Exact
                </button>
                {[50, 100, 200, 500, 2000].map((denom) => (
                  <button
                    key={denom}
                    type="button"
                    onClick={() => {
                      onSetFullPayment(false);
                      onSetAmountTendered(denom * 100);
                    }}
                    className="px-2 py-1 rounded bg-surface-secondary hover:bg-brand-subtle text-text-secondary hover:text-brand text-[11px] font-mono transition-colors border border-border-default cursor-pointer"
                  >
                    {currencySymbol}{denom}
                  </button>
                ))}
              </div>

              {/* Change Due Calculator for Cash Only */}
              <div
                className={`p-2.5 rounded-lg border text-center mt-2 transition-colors ${
                  totals.isChange
                    ? 'bg-success-bg border-success/30 text-success'
                    : 'bg-warning-bg border-warning/30 text-warning'
                }`}
              >
                <div className="text-[10px] font-bold uppercase tracking-wider">
                  {totals.isChange ? 'Change to Return' : 'Balance Due'}
                </div>
                <div className="text-xl font-bold financial-value mt-0.5">
                  {formatPaise(totals.balancePaise)}
                </div>
              </div>
            </div>
          )}

          {bill.paymentMethod === 'UPI' && (
            <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2 animate-fade-in">
              <span className="text-xs font-semibold text-text-primary block">UPI / QR Reference</span>
              <input
                type="text"
                placeholder="UTR / Transaction Reference (Optional)"
                value={upiRefNumber}
                onChange={(e) => setUpiRefNumber(e.target.value)}
                className="w-full bg-surface-secondary border border-border-strong rounded-lg px-3 py-1.5 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand"
              />
              <p className="text-[11px] text-text-muted">
                Collect payment via customer QR code scan before completing sale.
              </p>
            </div>
          )}

          {bill.paymentMethod === 'CARD' && (
            <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2 animate-fade-in">
              <span className="text-xs font-semibold text-text-primary block">Card Approval Code / Ref</span>
              <input
                type="text"
                placeholder="POS Terminal Approval Code (Optional)"
                value={cardRefNumber}
                onChange={(e) => setCardRefNumber(e.target.value)}
                className="w-full bg-surface-secondary border border-border-strong rounded-lg px-3 py-1.5 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand"
              />
              <p className="text-[11px] text-text-muted">
                Swipe/insert card in card terminal and verify approved transaction.
              </p>
            </div>
          )}

          {bill.paymentMethod === 'GIFT_CARD' && (
            <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2 animate-fade-in">
              <span className="text-xs font-semibold text-text-primary block">Gift Card / Voucher Code</span>
              <input
                type="text"
                placeholder="Gift card number or voucher code (Optional)"
                className="w-full bg-surface-secondary border border-border-strong rounded-lg px-3 py-1.5 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand"
              />
              <p className="text-[11px] text-text-muted">
                Redeem customer gift card voucher for this order settlement.
              </p>
            </div>
          )}

          {/* Optional Bill Notes */}
          {showNotes && (
            <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2">
              <label className="text-xs font-semibold text-text-primary block">Bill Notes</label>
              <textarea
                rows={2}
                value={notes}
                onChange={(e) => onNotesChange?.(e.target.value)}
                placeholder="Internal remarks or delivery notes…"
                className="w-full bg-surface-secondary border border-border-strong rounded-lg px-3 py-2 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand resize-none"
              />
            </div>
          )}
        </div>

        {/* 7. Complete Sale Persistent Button */}
        <div className="pt-2 border-t border-border-default">
          {primaryAction && (
            <button
              type="button"
              onClick={primaryAction.onClick}
              disabled={primaryAction.disabled}
              className="w-full py-3 rounded-xl bg-brand hover:bg-brand-hover text-white font-bold text-sm shadow-xs transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2 cursor-pointer"
            >
              {primaryAction.pending ? (
                <span>{primaryAction.pendingLabel || 'Processing...'}</span>
              ) : (
                <>
                  {PrimaryIcon && <PrimaryIcon className="w-4 h-4" />}
                  <span>{primaryAction.label}</span>
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
