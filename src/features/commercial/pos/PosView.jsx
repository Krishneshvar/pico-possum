import { useState, useEffect, useRef, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Printer, Plus, Trash2, X } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { usePosStore, computeBillTotals } from '../../../stores/pos.store';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
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

  // Unfiltered catalog for authoritative stock check
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
      queryClient.invalidateQueries({ queryKey: ['sale_details'] });
      queryClient.invalidateQueries({ queryKey: ['stock_movements'] });
      queryClient.invalidateQueries({ queryKey: ['product_flow'] });
      queryClient.invalidateQueries({ queryKey: ['sales_report_summary'] });
      queryClient.invalidateQueries({ queryKey: ['top_products'] });
      queryClient.invalidateQueries({ queryKey: ['top_categories'] });
      queryClient.invalidateQueries({ queryKey: ['multi_year_sales'] });
      queryClient.invalidateQueries({ queryKey: ['sales_breakdown'] });
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setCompletedSale(response);
      setShowPrintModal(true);
      clearCurrentBill();
      toast.success(`Invoice ${response.sale.invoice_number} completed successfully`, {
        title: 'Sale Completed',
      });
    },
    onError: (err) => {
      toast.error(`Sale failed: ${err}`, { title: 'Checkout Failed' });
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
            { title: 'Insufficient Stock' }
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
          { title: 'Stock Limit' }
        );
        return;
      }
    }
    addItem(product);
  };

  const changeItemQty = (productId, nextQty) => {
    const item = currentBill.items.find((it) => it.productId === productId);
    if (!item) return;

    if (nextQty <= 0) {
      updateItemQty(productId, 0);
      return;
    }

    if (!allowNegativeStock) {
      const available = productStockById.get(productId)?.current_stock;
      if (available != null && nextQty > available) {
        toast.warning(
          `Only ${available} unit${available === 1 ? '' : 's'} of "${item.name}" available in stock.`,
          { title: 'Stock Limit' }
        );
        return;
      }
    }

    updateItemQty(productId, nextQty);
  };

  const handlePrintReceipt = () => {
    toast.info('Opening system print dialog');
    window.print();
  };

  const handleClearCart = async () => {
    if (currentBill.items.length === 0) {
      toast.info(`Bill ${activeBillIndex + 1} is already empty`);
      return;
    }
    const { confirmed } = await confirmDialog({
      title: 'Clear Cart',
      message: `Remove all ${currentBill.items.length} items from Bill ${activeBillIndex + 1}?`,
      confirmLabel: 'Clear Cart',
      cancelLabel: 'Keep Items',
      tone: 'danger',
    });
    if (confirmed) {
      clearCurrentBill();
      toast.info(`Bill ${activeBillIndex + 1} cleared`);
    }
  };

  const primaryAction = {
    label: 'Complete Sale [F12]',
    pendingLabel: 'Processing Sale...',
    icon: CheckCircle2,
    onClick: handleCompleteSale,
    disabled: saleMutation.isPending || currentBill.items.length === 0,
    pending: saleMutation.isPending,
  };

  return (
    <div className="flex-1 flex flex-col h-full overflow-hidden bg-canvas">
      {/* ── POS Bill Tabs Bar ────────────────────────────────────────── */}
      <div className="h-11 bg-surface border-b border-border-default flex items-center px-4 gap-2 select-none justify-between shrink-0">
        <div className="flex items-center gap-1.5 overflow-x-auto">
          {bills.map((b, idx) => {
            const isActive = idx === activeBillIndex;
            const itemCount = b.items.length;
            return (
              <button
                key={idx}
                type="button"
                onClick={() => switchBill(idx)}
                className={`flex items-center gap-2 px-3 py-1 rounded-lg text-xs font-semibold transition-colors cursor-pointer ${
                  isActive
                    ? 'bg-brand text-white shadow-2xs'
                    : 'bg-surface-secondary text-text-secondary hover:text-text-primary border border-border-default'
                }`}
              >
                <span>Bill {idx + 1}</span>
                {itemCount > 0 && (
                  <span
                    className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                      isActive ? 'bg-white/20 text-white' : 'bg-brand-subtle text-brand'
                    }`}
                  >
                    {itemCount}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="text-xs text-text-muted flex items-center gap-3">
          <span>
            Active: <strong className="text-text-primary font-semibold">Bill {activeBillIndex + 1}</strong>
          </span>
          <button
            type="button"
            onClick={handleClearCart}
            disabled={currentBill.items.length === 0}
            className="text-[11px] text-text-secondary hover:text-danger px-2 py-1 rounded bg-surface-secondary hover:bg-danger-bg border border-border-default transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Clear Cart
          </button>
        </div>
      </div>

      {/* POS Cart and Checkout Editor */}
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
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-6 shadow-xl space-y-4 animate-scale-in">
            <div className="text-center space-y-2">
              <div className="w-12 h-12 rounded-full bg-success-bg text-success flex items-center justify-center mx-auto">
                <CheckCircle2 className="w-7 h-7" />
              </div>
              <h3 className="text-base font-bold text-text-primary">Sale Completed</h3>
              <p className="text-xs text-text-muted">
                Invoice Number:{' '}
                <strong className="text-text-primary font-mono">{completedSale.sale.invoice_number}</strong>
              </p>
            </div>

            {/* Change banner */}
            {completedSale.change_paise > 0 && (
              <div className="bg-surface-secondary p-3.5 rounded-xl border border-border-default text-center">
                <div className="text-[11px] text-text-muted uppercase font-bold tracking-wider">
                  Change to Return
                </div>
                <div className="text-2xl font-black text-text-primary financial-value mt-0.5">
                  {formatPaise(completedSale.change_paise)}
                </div>
              </div>
            )}

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
            <div className="grid grid-cols-2 gap-2.5 pt-2">
              <button
                type="button"
                onClick={handlePrintReceipt}
                className="py-2 px-3 rounded-xl bg-surface hover:bg-surface-secondary text-text-primary text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors border border-border-default cursor-pointer"
              >
                <Printer className="w-4 h-4 text-text-muted" />
                <span>Print Receipt</span>
              </button>
              <button
                type="button"
                onClick={() => setShowPrintModal(false)}
                className="py-2 px-3 rounded-xl bg-brand hover:bg-brand-hover text-white text-xs font-bold flex items-center justify-center gap-1.5 transition-colors shadow-2xs cursor-pointer"
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
