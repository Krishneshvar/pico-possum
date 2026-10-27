import { useState, useEffect, useRef, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Printer } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { usePosStore, computeBillTotals } from '../../../stores/pos.store';
import { toast } from '../../../stores/toast.store';
import { formatPaise, getCurrencySymbol } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import PosBillEditor from './PosBillEditor';
import AddCustomerModal from './AddCustomerModal';

export default function PosView() {
  const queryClient = useQueryClient();
  const searchInputRef = useRef(null);
  const currencySymbol = getCurrencySymbol();

  // Store state
  const {
    activeBillIndex,
    bills,
    switchBill,
    nextEmptyBill,
    hydrateOpenBills,
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

  useEffect(() => {
    hydrateOpenBills();
  }, [hydrateOpenBills]);

  const currentBill = getCurrentBill();
  const totals = computeBillTotals(currentBill);

  // Local UI state
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState(null);
  const [searchFocused, setSearchFocused] = useState(false);
  const [completedSale, setCompletedSale] = useState(null);
  const [showPrintModal, setShowPrintModal] = useState(false);
  const [customerModalOpen, setCustomerModalOpen] = useState(false);

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

  // Unfiltered catalog used for authoritative stock lookups in the cart.
  const { data: stockProducts = [] } = useQuery({
    queryKey: ['products', 'stock-catalog'],
    queryFn: () => tauriApi.getProducts(null, null),
  });

  const allowNegativeStock = generalSettings?.allow_negative_stock ?? false;

  const productStockById = useMemo(() => {
    const map = new Map();
    for (const p of stockProducts) map.set(p.id, p);
    return map;
  }, [stockProducts]);

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
      toast.success(`Invoice ${response.sale.invoice_number} completed successfully`, {
        title: 'Sale completed',
      });
    },
    onError: (err) => {
      toast.error(`Sale failed: ${err}`, { title: 'Checkout failed' });
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

      // Ctrl + N -> Next/new empty bill tab
      if (isCmd && e.key.toLowerCase() === 'n') {
        e.preventDefault();
        nextEmptyBill();
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
      toast.warning('Please add items to cart before completing checkout.');
      return;
    }

    if (!allowNegativeStock) {
      for (const item of currentBill.items) {
        const available = productStockById.get(item.productId)?.current_stock;
        if (available != null && item.quantity > available) {
          toast.warning(
            `Cannot complete sale: "${item.name}" has only ${available} unit${available === 1 ? '' : 's'} in stock (cart: ${item.quantity}).`,
            { title: 'Insufficient stock' }
          );
          return;
        }
      }
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

  const addProductToCart = (product) => {
    if (!allowNegativeStock) {
      const available =
        productStockById.get(product.id)?.current_stock ?? product.current_stock ?? 0;
      const inCart = currentBill.items.find((it) => it.productId === product.id)?.quantity || 0;

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
    addItem(product);
  };

  const changeItemQty = (item, nextQty) => {
    if (nextQty <= 0) {
      updateItemQty(item.productId, 0);
      return;
    }

    if (!allowNegativeStock) {
      const available = productStockById.get(item.productId)?.current_stock;
      if (available != null && nextQty > available) {
        toast.warning(
          `Only ${available} unit${available === 1 ? '' : 's'} of "${item.name}" available in stock.`,
          { title: 'Stock limit reached' }
        );
        return;
      }
    }

    updateItemQty(item.productId, nextQty);
  };

  const handlePrintReceipt = () => {
    toast.info('Opening the system print dialog');
    window.print();
  };

  const handleClearCart = () => {
    if (currentBill.items.length === 0) {
      toast.info(`Bill ${activeBillIndex + 1} is already empty`);
      return;
    }
    clearCurrentBill();
    toast.info(`Bill ${activeBillIndex + 1} cleared`);
  };

  const primaryAction = {
    label: 'Complete Sale [F12]',
    pendingLabel: 'Processing...',
    icon: CheckCircle2,
    onClick: handleCompleteSale,
    disabled: saleMutation.isPending || currentBill.items.length === 0,
    pending: saleMutation.isPending,
  };

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
            onClick={handleClearCart}
            className="text-[11px] text-slate-400 hover:text-rose-400 px-2 py-1 rounded bg-slate-800/60 hover:bg-slate-800 transition-colors"
          >
            Clear Cart
          </button>
        </div>
      </div>

      <PosBillEditor
        bill={currentBill}
        totals={totals}
        products={products}
        categories={categories}
        customers={customers}
        stockProducts={stockProducts}
        allowNegativeStock={allowNegativeStock}
        currencySymbol={currencySymbol}
        searchQuery={searchQuery}
        onSearchQueryChange={setSearchQuery}
        selectedCategory={selectedCategory}
        onSelectedCategoryChange={setSelectedCategory}
        searchFocused={searchFocused}
        onSearchFocusedChange={setSearchFocused}
        searchInputRef={searchInputRef}
        onAddItem={addProductToCart}
        onUpdateItemQty={changeItemQty}
        onUpdateItemPrice={updateItemPrice}
        onUpdateItemDiscount={updateItemDiscount}
        onRemoveItem={removeItem}
        onSetCustomer={setCustomer}
        onSetDiscount={setDiscount}
        onSetPaymentMethod={setPaymentMethod}
        onSetFullPayment={setFullPayment}
        onSetAmountTendered={setAmountTendered}
        onRequestAddCustomer={() => setCustomerModalOpen(true)}
        primaryAction={primaryAction}
      />

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

      <AddCustomerModal
        open={customerModalOpen}
        onClose={() => setCustomerModalOpen(false)}
        onCreated={(saved) => setCustomer(saved)}
      />
    </div>
  );
}
