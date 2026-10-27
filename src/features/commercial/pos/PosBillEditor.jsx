import { useMemo } from 'react';
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
} from 'lucide-react';
import { formatPaise, rupeesToPaise } from '../../../lib/formatters';

const EMPTY_STOCK_ALLOWANCE = new Map();

/**
 * Shared POS bill workspace used both for creating a sale (PosView) and for
 * editing an existing bill (BillDetailView).
 *
 * The parent owns the bill state and all mutations, which keeps this component
 * purely presentational while guaranteeing both screens stay visually in sync.
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
  const stockById = useMemo(() => {
    const map = new Map();
    for (const p of stockProducts) map.set(p.id, p);
    return map;
  }, [stockProducts]);

  // Stock already reserved by this bill is available to the edit session again.
  const availableStockFor = (productId) => {
    const product = stockById.get(productId);
    if (!product) return undefined;
    return (product.current_stock || 0) + (stockAllowanceByProduct.get(productId) || 0);
  };

  const filteredSearchProducts = products.slice(0, 8);
  const PrimaryIcon = primaryAction?.icon;

  return (
    <div className="flex-1 flex overflow-hidden">
      {/* Left Area: Search + Cart Table */}
      <div className="flex-1 flex flex-col border-r border-slate-800/80 overflow-hidden">
        {/* Product Search Bar */}
        <div className="p-3 bg-slate-900/60 border-b border-slate-800/80 relative">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              ref={searchInputRef}
              type="text"
              placeholder="Scan barcode, SKU, or search product name... (Ctrl+K)"
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
                  setTimeout(() => searchInputRef?.current?.focus(), 50);
                }
              }}
              className="w-full pl-10 pr-24 py-2.5 rounded-xl bg-slate-950/80 border border-slate-800 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 transition-all shadow-inner"
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-slate-500 font-mono bg-slate-800 px-1.5 py-0.5 rounded border border-slate-700">
              ↵ Enter
            </span>
          </div>

          {/* Live Autocomplete Dropdown */}
          {searchFocused && searchQuery && (
            <div className="absolute left-3 right-3 top-full mt-1 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl z-50 overflow-hidden divide-y divide-slate-800/60">
              {filteredSearchProducts.length > 0 ? (
                filteredSearchProducts.map((p) => {
                  const available = availableStockFor(p.id) ?? p.current_stock;
                  const isOut = !allowNegativeStock && available <= 0;
                  const isLow =
                    !allowNegativeStock && !isOut && available <= (p.min_stock_alert ?? 5);

                  return (
                    <button
                      key={p.id}
                      onClick={() => {
                        onAddItem(p);
                        onSearchQueryChange('');
                        onSearchFocusedChange(false);
                      }}
                      className={`w-full flex items-center justify-between px-4 py-2.5 text-left hover:bg-emerald-500/10 transition-colors group ${
                        isOut ? 'opacity-60' : ''
                      }`}
                    >
                      <div>
                        <div className="text-sm font-semibold text-slate-200 group-hover:text-emerald-400">
                          {p.name}
                        </div>
                        <div className="text-xs text-slate-400 flex items-center gap-2">
                          <span>SKU: {p.sku || '-'}</span>
                          <span>•</span>
                          <span
                            className={
                              isOut
                                ? 'text-rose-400 font-semibold'
                                : isLow
                                ? 'text-amber-400 font-semibold'
                                : ''
                            }
                          >
                            {isOut ? 'Out of stock' : `Stock: ${available}`}
                          </span>
                        </div>
                      </div>
                      <div className="text-sm font-bold text-emerald-400 font-mono">
                        {formatPaise(p.mrp_paise)}
                      </div>
                    </button>
                  );
                })
              ) : (
                <div className="p-4 text-center text-sm text-slate-500">
                  No products matching "{searchQuery}"
                </div>
              )}
            </div>
          )}

          {/* Quick Category Filters */}
          <div className="flex items-center gap-1.5 mt-2 overflow-x-auto pb-1 text-xs">
            <button
              onClick={() => onSelectedCategoryChange(null)}
              className={`px-2.5 py-1 rounded-md font-medium transition-colors ${
                selectedCategory === null
                  ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                  : 'bg-slate-800 text-slate-400 hover:text-slate-200'
              }`}
            >
              All Items
            </button>
            {categories.map((c) => (
              <button
                key={c.id}
                onClick={() => onSelectedCategoryChange(c.id === selectedCategory ? null : c.id)}
                className={`px-2.5 py-1 rounded-md font-medium whitespace-nowrap transition-colors ${
                  selectedCategory === c.id
                    ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                    : 'bg-slate-800 text-slate-400 hover:text-slate-200'
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
        </div>

        {/* Cart Items Table */}
        <div className="flex-1 overflow-y-auto">
          {bill.items.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-slate-500 p-8">
              <div className="w-16 h-16 rounded-2xl bg-slate-900 flex items-center justify-center mb-3 text-slate-600 border border-slate-800">
                <Receipt className="w-8 h-8" />
              </div>
              <h3 className="text-base font-semibold text-slate-400">Cart is empty</h3>
              <p className="text-xs text-slate-500 mt-1 max-w-xs text-center">
                Search a product above or press <kbd className="text-slate-300 bg-slate-800 px-1 py-0.5 rounded font-mono">Ctrl+K</kbd> to add items.
              </p>
            </div>
          ) : (
            <table className="w-full text-left border-collapse">
              <thead className="bg-slate-900/90 text-[11px] font-semibold text-slate-400 uppercase tracking-wider sticky top-0 border-b border-slate-800 z-10">
                <tr>
                  <th className="py-2.5 px-3 w-10 text-center">#</th>
                  <th className="py-2.5 px-3 w-20">SKU</th>
                  <th className="py-2.5 px-3">Item</th>
                  <th className="py-2.5 px-3 w-28 text-center">Qty</th>
                  <th className="py-2.5 px-3 w-24 text-right">Price</th>
                  <th className="py-2.5 px-3 w-20 text-right">MRP</th>
                  <th className="py-2.5 px-3 w-20 text-right">Disc</th>
                  <th className="py-2.5 px-3 w-28 text-right">Total</th>
                  <th className="py-2.5 px-2 w-10 text-center"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/50 text-sm">
                {bill.items.map((item, index) => {
                  const lineTotal = (item.unitPricePaise * item.quantity) - (item.discountPaise || 0);
                  const availableStock = availableStockFor(item.productId);
                  const exceedsStock =
                    !allowNegativeStock && availableStock != null && item.quantity > availableStock;

                  return (
                    <tr key={item.productId} className="hover:bg-slate-900/40 transition-colors group">
                      <td className="py-2 px-3 text-center text-xs text-slate-500 font-mono">
                        {index + 1}
                      </td>
                      <td className="py-2 px-3 text-xs text-slate-400 font-mono truncate max-w-[80px]">
                        {item.sku || '-'}
                      </td>
                      <td className="py-2 px-3 font-medium text-slate-200">
                        {item.name}
                        {exceedsStock && (
                          <div className="text-[10px] text-rose-400 font-semibold">
                            Only {availableStock} in stock
                          </div>
                        )}
                      </td>
                      <td className="py-2 px-3">
                        <div className="flex items-center justify-center gap-1 bg-slate-900 border border-slate-700/80 rounded-lg p-0.5">
                          <button
                            onClick={() => onUpdateItemQty(item.productId, item.quantity - 1)}
                            className="w-6 h-6 rounded flex items-center justify-center text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                          >
                            <Minus className="w-3 h-3" />
                          </button>
                          <input
                            type="number"
                            min="1"
                            value={item.quantity}
                            onChange={(e) => onUpdateItemQty(item.productId, parseInt(e.target.value, 10) || 1)}
                            className="w-9 text-center bg-transparent font-semibold text-xs text-slate-200 focus:outline-none"
                          />
                          <button
                            onClick={() => onUpdateItemQty(item.productId, item.quantity + 1)}
                            className="w-6 h-6 rounded flex items-center justify-center text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                          >
                            <Plus className="w-3 h-3" />
                          </button>
                        </div>
                      </td>
                      <td className="py-2 px-3 text-right">
                        <input
                          type="number"
                          step="0.01"
                          value={(item.unitPricePaise / 100).toFixed(2)}
                          onChange={(e) => onUpdateItemPrice(item.productId, rupeesToPaise(e.target.value))}
                          className="w-20 text-right bg-slate-900/50 border border-slate-800 focus:border-emerald-500 rounded px-1.5 py-0.5 font-mono text-xs text-slate-200"
                        />
                      </td>
                      <td className="py-2 px-3 text-right text-xs text-slate-400 font-mono">
                        {formatPaise(item.mrpPaise)}
                      </td>
                      <td className="py-2 px-3 text-right">
                        <input
                          type="number"
                          step="0.01"
                          value={item.discountPaise ? (item.discountPaise / 100).toFixed(2) : ''}
                          placeholder="0"
                          onChange={(e) => onUpdateItemDiscount(item.productId, rupeesToPaise(e.target.value))}
                          className="w-16 text-right bg-slate-900/50 border border-slate-800 focus:border-emerald-500 rounded px-1.5 py-0.5 font-mono text-xs text-slate-300"
                        />
                      </td>
                      <td className="py-2 px-3 text-right font-bold text-slate-100 font-mono">
                        {formatPaise(lineTotal)}
                      </td>
                      <td className="py-2 px-2 text-center">
                        <button
                          onClick={() => onRemoveItem(item.productId)}
                          className="text-slate-500 hover:text-rose-400 transition-colors p-1"
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
          )}
        </div>

        {/* Bottom Table Summary */}
        <div className="h-10 bg-slate-900/80 border-t border-slate-800 px-4 flex items-center justify-between text-xs text-slate-400 font-medium">
          <div className="flex items-center gap-4">
            <span>Lines: <strong className="text-slate-200">{bill.items.length}</strong></span>
            <span>Total Qty: <strong className="text-slate-200">{totals.totalQty}</strong></span>
          </div>
          <div>
            <span>Cart Subtotal: <strong className="text-emerald-400 font-mono">{formatPaise(totals.subtotalPaise)}</strong></span>
          </div>
        </div>
      </div>

      {/* Right Panel: Customer, Discount & Payment Checkout */}
      <div className="w-96 bg-slate-900/70 flex flex-col justify-between overflow-y-auto p-4 space-y-4">
        <div className="space-y-4">
          {/* Customer Attachment */}
          <div className="glass-card p-3 rounded-xl border border-slate-800 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                <User className="w-3.5 h-3.5 text-emerald-400" /> Customer
              </span>
              <button
                onClick={onRequestAddCustomer}
                className="text-[11px] text-emerald-400 hover:underline"
              >
                {bill.customer ? 'Change' : '+ Add'}
              </button>
            </div>

            {bill.customer ? (
              <div className="flex items-center justify-between bg-slate-950/60 p-2 rounded-lg border border-slate-800 text-xs">
                <div>
                  <div className="font-semibold text-slate-200">{bill.customer.name}</div>
                  <div className="text-slate-400 text-[11px]">{bill.customer.phone || 'No phone'}</div>
                </div>
                <button
                  onClick={() => onSetCustomer(null)}
                  className="text-slate-500 hover:text-rose-400"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            ) : (
              <select
                value={bill.customer?.id || ''}
                onChange={(e) => {
                  const c = customers.find((cust) => cust.id === e.target.value);
                  onSetCustomer(c || null);
                }}
                className="w-full bg-slate-950/80 border border-slate-800 text-xs text-slate-300 rounded-lg p-2 focus:border-emerald-500 focus:outline-none"
              >
                <option value="">Walk-in Customer</option>
                {customers.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} {c.phone ? `(${c.phone})` : ''}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Payment Method Selector */}
          <div className="glass-card p-3 rounded-xl border border-slate-800 space-y-2">
            <label className="text-xs font-semibold text-slate-300">Payment Method</label>
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
                    onClick={() => onSetPaymentMethod(pm.id)}
                    className={`flex items-center gap-2 p-2 rounded-lg text-xs font-medium border transition-all ${
                      isSelected
                        ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/50 shadow-sm'
                        : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                    }`}
                  >
                    <Icon className="w-3.5 h-3.5" />
                    <span>{pm.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Overall Discount */}
          <div className="glass-card p-3 rounded-xl border border-slate-800 space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-slate-300">Bill Discount</span>
              <div className="flex bg-slate-950 rounded-lg p-0.5 border border-slate-800">
                <button
                  onClick={() => onSetDiscount('FIXED', bill.discountValue)}
                  className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                    bill.discountType === 'FIXED' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'
                  }`}
                >
                  {currencySymbol} Flat
                </button>
                <button
                  onClick={() => onSetDiscount('PERCENT', bill.discountValue)}
                  className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                    bill.discountType === 'PERCENT' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'
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
              placeholder="0"
              value={bill.discountValue || ''}
              onChange={(e) => onSetDiscount(bill.discountType, e.target.value)}
              className="w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3 py-1.5 text-sm font-mono text-slate-100 focus:outline-none focus:border-emerald-500"
            />
          </div>

          {/* Tendered Amount */}
          <div className="glass-card p-3 rounded-xl border border-slate-800 space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-slate-300">Amount Tendered</span>
              <button
                onClick={() => {
                  const next = !bill.isFullPayment;
                  onSetFullPayment(next);
                  if (next) onSetAmountTendered(totals.totalPaise);
                }}
                className={`text-[11px] px-2 py-0.5 rounded font-medium border ${
                  bill.isFullPayment
                    ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
                    : 'bg-slate-950 text-slate-400 border-slate-800'
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
              className={`w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3 py-2 text-base font-mono text-slate-100 focus:outline-none focus:border-emerald-500 ${
                bill.isFullPayment ? 'opacity-80' : ''
              }`}
            />

            {bill.paymentMethod === 'CASH' && (
              <div className="flex flex-wrap gap-1.5 pt-1">
                <button
                  type="button"
                  onClick={() => {
                    onSetFullPayment(true);
                    onSetAmountTendered(totals.totalPaise);
                  }}
                  className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white text-[10px] font-mono font-bold transition-colors"
                >
                  Exact
                </button>
                {[50, 100, 200, 500, 1000, 2000].map((denom) => (
                  <button
                    key={denom}
                    type="button"
                    onClick={() => {
                      onSetFullPayment(false);
                      onSetAmountTendered(denom * 100);
                    }}
                    className="px-2 py-1 rounded bg-slate-800/80 hover:bg-emerald-500/20 text-slate-300 hover:text-emerald-400 text-[10px] font-mono transition-colors border border-slate-700/60"
                  >
                    {currencySymbol}{denom}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Optional bill notes (used when editing an existing bill) */}
          {showNotes && (
            <div className="glass-card p-3 rounded-xl border border-slate-800 space-y-2">
              <label className="text-xs font-semibold text-slate-300">Bill Notes</label>
              <textarea
                rows={2}
                value={notes}
                onChange={(e) => onNotesChange?.(e.target.value)}
                placeholder="Internal notes or remarks…"
                className="w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-emerald-500 resize-none"
              />
            </div>
          )}
        </div>

        {/* Totals & Primary Action */}
        <div className="space-y-3 pt-3 border-t border-slate-800">
          <div className="space-y-1.5 text-xs text-slate-300">
            <div className="flex justify-between">
              <span>Subtotal</span>
              <span className="font-mono">{formatPaise(totals.subtotalPaise)}</span>
            </div>
            {totals.discountPaise > 0 && (
              <div className="flex justify-between text-amber-400">
                <span>Discount</span>
                <span className="font-mono">-{formatPaise(totals.discountPaise)}</span>
              </div>
            )}
            {totals.taxPaise > 0 && (
              <div className="flex justify-between text-slate-400">
                <span>Tax Amount</span>
                <span className="font-mono">{formatPaise(totals.taxPaise)}</span>
              </div>
            )}
            <div className="flex justify-between text-base font-bold text-slate-100 pt-1 border-t border-slate-800">
              <span>Total Due</span>
              <span className="font-mono text-emerald-400 text-lg">{formatPaise(totals.totalPaise)}</span>
            </div>
          </div>

          {/* Change to Return / Balance Due Card */}
          <div
            className={`p-3 rounded-xl border text-center transition-all ${
              totals.isChange
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                : 'bg-amber-500/10 border-amber-500/30 text-amber-400'
            }`}
          >
            <div className="text-[11px] font-bold uppercase tracking-wider">
              {totals.isChange ? 'Change to Return' : 'Balance Due'}
            </div>
            <div className="text-2xl font-black font-mono mt-0.5">
              {formatPaise(totals.balancePaise)}
            </div>
          </div>

          {/* Primary Action */}
          {primaryAction && (
            <button
              onClick={primaryAction.onClick}
              disabled={primaryAction.disabled}
              className="w-full py-3.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-slate-950 font-bold text-sm tracking-wide shadow-lg shadow-emerald-500/25 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
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
