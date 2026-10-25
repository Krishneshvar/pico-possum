import { useState, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
  CheckCircle2,
  Printer,
  X,
  Sparkles,
  Receipt
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { usePosStore, computeBillTotals } from '../../../stores/pos.store';
import { formatPaise, rupeesToPaise } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';

export default function PosView() {
  const queryClient = useQueryClient();
  const searchInputRef = useRef(null);

  // Store state
  const {
    activeBillIndex,
    bills,
    switchBill,
    getCurrentBill,
    addItem,
    updateItemQty,
    updateItemPrice,
    updateItemDiscount,
    removeItem,
    setCustomer,
    setDiscount,
    setPaymentMethod,
    setFullPayment,
    setAmountTendered,
    clearCurrentBill,
  } = usePosStore();

  const currentBill = getCurrentBill();
  const totals = computeBillTotals(currentBill);

  // Local UI state
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState(null);
  const [searchFocused, setSearchFocused] = useState(false);
  const [completedSale, setCompletedSale] = useState(null);
  const [showPrintModal, setShowPrintModal] = useState(false);
  const [customerModalOpen, setCustomerModalOpen] = useState(false);
  const [newCustomer, setNewCustomer] = useState({ name: '', phone: '', address: '' });

  // Data queries
  const { data: products = [] } = useQuery({
    queryKey: ['products', selectedCategory, searchQuery],
    queryFn: () => tauriApi.getProducts(selectedCategory, searchQuery || null),
  });

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => tauriApi.getCategories(),
  });

  const { data: customers = [] } = useQuery({
    queryKey: ['customers'],
    queryFn: () => tauriApi.getCustomers(),
  });

  const { data: billSettings } = useQuery({
    queryKey: ['bill_settings'],
    queryFn: () => tauriApi.getBillSettings(),
  });

  const { data: generalSettings } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  // Complete Sale Mutation
  const saleMutation = useMutation({
    mutationFn: (payload) => tauriApi.processSale(payload),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['sales'] });
      queryClient.invalidateQueries({ queryKey: ['reports'] });
      setCompletedSale(response);
      setShowPrintModal(true);
      clearCurrentBill();
    },
    onError: (err) => {
      alert(`Sale failed: ${err}`);
    },
  });

  // Keyboard shortcuts listener
  useEffect(() => {
    const handleKeyDown = (e) => {
      const isCmd = e.ctrlKey || e.metaKey;

      // Ctrl + K -> Focus search
      if (isCmd && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        searchInputRef.current?.focus();
        searchInputRef.current?.select();
        return;
      }

      // Ctrl + 1..9 -> Switch bill tab
      if (isCmd && e.key >= '1' && e.key <= '9') {
        e.preventDefault();
        const targetIndex = parseInt(e.key, 10) - 1;
        switchBill(targetIndex);
        return;
      }

      // F12 or Ctrl + Enter -> Complete sale
      if (e.key === 'F12' || (isCmd && e.key === 'Enter')) {
        e.preventDefault();
        handleCompleteSale();
        return;
      }

      // Escape -> Close popups or blur
      if (e.key === 'Escape') {
        setSearchFocused(false);
        setShowPrintModal(false);
        setCustomerModalOpen(false);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [currentBill, totals]);

  const handleCompleteSale = () => {
    if (!currentBill.items.length) {
      alert('Please add items to cart before completing checkout.');
      return;
    }

    const payload = {
      customer_id: currentBill.customer?.id || null,
      customer_name: currentBill.customer?.name || null,
      customer_phone: currentBill.customer?.phone || null,
      customer_address: currentBill.customer?.address || null,
      items: currentBill.items.map((it) => ({
        product_id: it.productId,
        quantity: it.quantity,
        unit_price_paise: it.unitPricePaise,
        discount_paise: it.discountPaise || 0,
      })),
      discount_paise: totals.discountPaise,
      payment_method: currentBill.paymentMethod,
      paid_paise: totals.tenderedPaise,
      notes: null,
    };

    saleMutation.mutate(payload);
  };

  const handlePrintReceipt = () => {
    window.print();
  };

  const filteredSearchProducts = products.slice(0, 8);

  return (
    <div className="flex-1 flex flex-col h-full overflow-hidden bg-slate-950">
      {/* ── Bill Tabs Bar ──────────────────────────────────────────────── */}
      <div className="h-12 bg-slate-900 border-b border-slate-800 flex items-center px-4 gap-2 select-none justify-between">
        <div className="flex items-center gap-1.5 overflow-x-auto">
          {bills.map((b, idx) => {
            const isActive = idx === activeBillIndex;
            const itemCount = b.items.length;
            return (
              <button
                key={idx}
                onClick={() => switchBill(idx)}
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  isActive
                    ? 'bg-emerald-500 text-slate-950 shadow-md shadow-emerald-500/20'
                    : 'bg-slate-800/80 text-slate-300 hover:bg-slate-800 hover:text-white border border-slate-700/60'
                }`}
              >
                <span>Bill {idx + 1}</span>
                {itemCount > 0 && (
                  <span
                    className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                      isActive ? 'bg-slate-950/30 text-slate-950' : 'bg-emerald-500/20 text-emerald-400'
                    }`}
                  >
                    {itemCount}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="text-xs text-slate-400 flex items-center gap-2">
          <span>Active Tab: <strong className="text-emerald-400">Bill {activeBillIndex + 1}</strong></span>
          <button
            onClick={clearCurrentBill}
            className="text-[11px] text-slate-400 hover:text-rose-400 px-2 py-1 rounded bg-slate-800/60 hover:bg-slate-800 transition-colors"
          >
            Clear Cart
          </button>
        </div>
      </div>

      {/* ── Main POS Workspace ────────────────────────────────────────── */}
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
                onChange={(e) => setSearchQuery(e.target.value)}
                onFocus={() => setSearchFocused(true)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && filteredSearchProducts.length > 0) {
                    addItem(filteredSearchProducts[0]);
                    setSearchQuery('');
                    setSearchFocused(false);
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
                  filteredSearchProducts.map((p) => (
                    <button
                      key={p.id}
                      onClick={() => {
                        addItem(p);
                        setSearchQuery('');
                        setSearchFocused(false);
                      }}
                      className="w-full flex items-center justify-between px-4 py-2.5 text-left hover:bg-emerald-500/10 transition-colors group"
                    >
                      <div>
                        <div className="text-sm font-semibold text-slate-200 group-hover:text-emerald-400">
                          {p.name}
                        </div>
                        <div className="text-xs text-slate-400 flex items-center gap-2">
                          <span>SKU: {p.sku || '-'}</span>
                          <span>•</span>
                          <span>Stock: {p.current_stock}</span>
                        </div>
                      </div>
                      <div className="text-sm font-bold text-emerald-400 font-mono">
                        {formatPaise(p.mrp_paise)}
                      </div>
                    </button>
                  ))
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
                onClick={() => setSelectedCategory(null)}
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
                  onClick={() => setSelectedCategory(c.id === selectedCategory ? null : c.id)}
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
            {currentBill.items.length === 0 ? (
              <div className="h-full flex flex-col items-center justify-center text-slate-500 p-8">
                <div className="w-16 h-16 rounded-2xl bg-slate-900 flex items-center justify-center mb-3 text-slate-600 border border-slate-800">
                  <Receipt className="w-8 h-8" />
                </div>
                <h3 className="text-base font-semibold text-slate-400">Cart is empty</h3>
                <p className="text-xs text-slate-500 mt-1 max-w-xs text-center">
                  Search a product above or press <kbd className="text-slate-300 bg-slate-800 px-1 py-0.5 rounded font-mono">Ctrl+K</kbd> to add items to Bill {activeBillIndex + 1}.
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
                  {currentBill.items.map((item, index) => {
                    const lineTotal = (item.unitPricePaise * item.quantity) - (item.discountPaise || 0);
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
                        </td>
                        <td className="py-2 px-3">
                          <div className="flex items-center justify-center gap-1 bg-slate-900 border border-slate-700/80 rounded-lg p-0.5">
                            <button
                              onClick={() => updateItemQty(item.productId, item.quantity - 1)}
                              className="w-6 h-6 rounded flex items-center justify-center text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                            >
                              <Minus className="w-3 h-3" />
                            </button>
                            <input
                              type="number"
                              min="1"
                              value={item.quantity}
                              onChange={(e) => updateItemQty(item.productId, parseInt(e.target.value, 10) || 1)}
                              className="w-9 text-center bg-transparent font-semibold text-xs text-slate-200 focus:outline-none"
                            />
                            <button
                              onClick={() => updateItemQty(item.productId, item.quantity + 1)}
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
                            onChange={(e) => updateItemPrice(item.productId, rupeesToPaise(e.target.value))}
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
                            onChange={(e) => updateItemDiscount(item.productId, rupeesToPaise(e.target.value))}
                            className="w-16 text-right bg-slate-900/50 border border-slate-800 focus:border-emerald-500 rounded px-1.5 py-0.5 font-mono text-xs text-slate-300"
                          />
                        </td>
                        <td className="py-2 px-3 text-right font-bold text-slate-100 font-mono">
                          {formatPaise(lineTotal)}
                        </td>
                        <td className="py-2 px-2 text-center">
                          <button
                            onClick={() => removeItem(item.productId)}
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
              <span>Lines: <strong className="text-slate-200">{currentBill.items.length}</strong></span>
              <span>Total Qty: <strong className="text-slate-200">{totals.totalQty}</strong></span>
            </div>
            <div>
              <span>Cart Subtotal: <strong className="text-emerald-400 font-mono">{formatPaise(totals.subtotalPaise)}</strong></span>
            </div>
          </div>
        </div>

        {/* ── Right Panel: Customer, Discount & Payment Checkout ────────── */}
        <div className="w-96 bg-slate-900/70 flex flex-col justify-between overflow-y-auto p-4 space-y-4">
          <div className="space-y-4">
            {/* Customer Attachment */}
            <div className="glass-card p-3 rounded-xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                  <User className="w-3.5 h-3.5 text-emerald-400" /> Customer
                </span>
                <button
                  onClick={() => setCustomerModalOpen(true)}
                  className="text-[11px] text-emerald-400 hover:underline"
                >
                  {currentBill.customer ? 'Change' : '+ Add'}
                </button>
              </div>

              {currentBill.customer ? (
                <div className="flex items-center justify-between bg-slate-950/60 p-2 rounded-lg border border-slate-800 text-xs">
                  <div>
                    <div className="font-semibold text-slate-200">{currentBill.customer.name}</div>
                    <div className="text-slate-400 text-[11px]">{currentBill.customer.phone || 'No phone'}</div>
                  </div>
                  <button
                    onClick={() => setCustomer(null)}
                    className="text-slate-500 hover:text-rose-400"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              ) : (
                <select
                  value={currentBill.customer?.id || ''}
                  onChange={(e) => {
                    const c = customers.find((cust) => cust.id === e.target.value);
                    setCustomer(c || null);
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
                  const isSelected = currentBill.paymentMethod === pm.id;
                  return (
                    <button
                      key={pm.id}
                      onClick={() => setPaymentMethod(pm.id)}
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
                    onClick={() => setDiscount('FIXED', currentBill.discountValue)}
                    className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                      currentBill.discountType === 'FIXED' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'
                    }`}
                  >
                    ₹ Flat
                  </button>
                  <button
                    onClick={() => setDiscount('PERCENT', currentBill.discountValue)}
                    className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                      currentBill.discountType === 'PERCENT' ? 'bg-emerald-500 text-slate-950' : 'text-slate-400'
                    }`}
                  >
                    %
                  </button>
                </div>
              </div>
              <input
                type="number"
                min="0"
                step="1"
                placeholder="0"
                value={currentBill.discountValue || ''}
                onChange={(e) => setDiscount(currentBill.discountType, e.target.value)}
                className="w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3 py-1.5 text-sm font-mono text-slate-100 focus:outline-none focus:border-emerald-500"
              />
            </div>

            {/* Tendered Amount */}
            <div className="glass-card p-3 rounded-xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-slate-300">Amount Tendered</span>
                <button
                  onClick={() => {
                    const next = !currentBill.isFullPayment;
                    setFullPayment(next);
                    if (next) setAmountTendered(totals.totalPaise);
                  }}
                  className={`text-[11px] px-2 py-0.5 rounded font-medium border ${
                    currentBill.isFullPayment
                      ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
                      : 'bg-slate-950 text-slate-400 border-slate-800'
                  }`}
                >
                  {currentBill.isFullPayment ? 'Full Payment' : 'Custom Amount'}
                </button>
              </div>
              <input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={(totals.tenderedPaise / 100).toFixed(2)}
                disabled={currentBill.isFullPayment}
                onChange={(e) => setAmountTendered(rupeesToPaise(e.target.value))}
                className={`w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3 py-2 text-base font-mono text-slate-100 focus:outline-none focus:border-emerald-500 ${
                  currentBill.isFullPayment ? 'opacity-80' : ''
                }`}
              />
            </div>
          </div>

          {/* Totals & Checkout Button */}
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

            {/* Complete Sale Action */}
            <button
              onClick={handleCompleteSale}
              disabled={saleMutation.isPending || currentBill.items.length === 0}
              className="w-full py-3.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-slate-950 font-bold text-sm tracking-wide shadow-lg shadow-emerald-500/25 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {saleMutation.isPending ? (
                <span>Processing...</span>
              ) : (
                <>
                  <CheckCircle2 className="w-4 h-4" />
                  <span>Complete Sale [F12]</span>
                </>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* ── Completion & Receipt Print Modal ───────────────────────────── */}
      {showPrintModal && completedSale && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 shadow-2xl space-y-5 animate-in fade-in zoom-in-95">
            <div className="text-center space-y-2">
              <div className="w-14 h-14 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto shadow-inner">
                <CheckCircle2 className="w-8 h-8" />
              </div>
              <h3 className="text-lg font-bold text-slate-100">Sale Completed Successfully</h3>
              <p className="text-xs text-slate-400">
                Invoice: <strong className="text-emerald-400 font-mono">{completedSale.sale.invoice_number}</strong>
              </p>
            </div>

            {/* Change banner */}
            <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 text-center">
              <div className="text-xs text-slate-400 uppercase font-semibold">Change to Return</div>
              <div className="text-3xl font-black text-emerald-400 font-mono mt-1">
                {formatPaise(completedSale.change_paise)}
              </div>
            </div>

            {/* Hidden printable receipt container */}
            <div className="hidden">
              <ReceiptPrinter
                sale={completedSale.sale}
                items={completedSale.items}
                billSettings={billSettings}
                generalSettings={generalSettings}
              />
            </div>

            {/* Action buttons */}
            <div className="grid grid-cols-2 gap-3 pt-2">
              <button
                onClick={handlePrintReceipt}
                className="py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-semibold flex items-center justify-center gap-2 transition-colors border border-slate-700"
              >
                <Printer className="w-4 h-4" />
                <span>Print Receipt</span>
              </button>
              <button
                onClick={() => setShowPrintModal(false)}
                className="py-2.5 px-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 text-sm font-bold flex items-center justify-center gap-2 transition-colors shadow-md shadow-emerald-500/20"
              >
                <span>New Sale [Esc]</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Add Customer Modal ────────────────────────────────────────── */}
      {customerModalOpen && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h3 className="font-bold text-sm text-slate-100">Add New Customer</h3>
              <button onClick={() => setCustomerModalOpen(false)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Customer Name *</label>
                <input
                  type="text"
                  value={newCustomer.name}
                  onChange={(e) => setNewCustomer({ ...newCustomer, name: e.target.value })}
                  placeholder="e.g. Ramesh Kumar"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 block mb-1">Phone Number</label>
                <input
                  type="text"
                  value={newCustomer.phone}
                  onChange={(e) => setNewCustomer({ ...newCustomer, phone: e.target.value })}
                  placeholder="e.g. 9876543210"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 block mb-1">Address</label>
                <textarea
                  rows="2"
                  value={newCustomer.address}
                  onChange={(e) => setNewCustomer({ ...newCustomer, address: e.target.value })}
                  placeholder="Street / locality"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setCustomerModalOpen(false)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800"
              >
                Cancel
              </button>
              <button
                onClick={async () => {
                  if (!newCustomer.name.trim()) return;
                  try {
                    const saved = await tauriApi.createCustomer(newCustomer);
                    queryClient.invalidateQueries({ queryKey: ['customers'] });
                    setCustomer(saved);
                    setCustomerModalOpen(false);
                    setNewCustomer({ name: '', phone: '', address: '' });
                  } catch (err) {
                    alert(`Failed to add customer: ${err}`);
                  }
                }}
                className="px-4 py-1.5 rounded-lg text-xs font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400"
              >
                Save Customer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
