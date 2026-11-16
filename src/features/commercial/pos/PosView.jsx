import { useState, useEffect, useRef, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Printer } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { usePosStore, computeBillTotals } from '../../../stores/pos.store';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatPaise, getCurrencySymbol } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import PosBillEditor from './PosBillEditor';
import CustomerModal from '../customers/CustomerModal';
import { strings } from '../../../lib/strings';

export default function PosView() {
  const queryClient = useQueryClient();
  const searchInputRef = useRef(null);
  const amountTenderedInputRef = useRef(null);
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
    setNotes,
    clearCurrentBill,
  } = usePosStore();

  useEffect(() => {
    hydrateOpenBills();
  }, [hydrateOpenBills]);

  const currentBill = getCurrentBill();
  const totals = computeBillTotals(currentBill);

  // Local UI state
  const [searchQuery, setSearchQuery] = useState('');
  const [searchFocused, setSearchFocused] = useState(false);
  const [completedSale, setCompletedSale] = useState(null);
  const [showPrintModal, setShowPrintModal] = useState(false);
  const [customerModalOpen, setCustomerModalOpen] = useState(false);

  // Data queries
  const { data: products = [] } = useQuery({
    queryKey: ['products', searchQuery],
    queryFn: () => tauriApi.getProducts(null, searchQuery || null),
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
      toast.success(
        strings.pos.view.saleCompletedMessage(response.sale.invoice_number),
        { title: strings.pos.view.saleCompletedTitle }
      );
    },
    onError: (err) => {
      toast.error(strings.pos.view.saleFailed(err), {
        title: strings.pos.view.checkoutFailedTitle,
      });
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
      toast.warning(strings.pos.view.addItemsBeforeCheckout);
      return;
    }

    const requiresTender =
      !currentBill.paymentMethod ||
      currentBill.paymentMethod === 'CASH' ||
      currentBill.paymentMethod === 'GIFT_CARD';
    const isFull = currentBill.isFullPayment !== false;
    const tendered = currentBill.amountTenderedPaise || 0;
    const total = totals.totalPaise;

    // Amount tendered checks apply to cash and gift card payments; digital methods (UPI, Card) settle the exact amount
    if (requiresTender) {
      if (isFull) {
        if (total > 0 && tendered < total) {
          amountTenderedInputRef.current?.focus();
          amountTenderedInputRef.current?.select();
          toast.info(
            strings.pos.view.enterTenderedAmount(formatPaise(total)),
            { title: strings.pos.view.tenderRequiredTitle }
          );
          return;
        }
      } else {
        if (tendered <= 0) {
          amountTenderedInputRef.current?.focus();
          amountTenderedInputRef.current?.select();
          toast.info(
            strings.pos.view.enterPartialPayment(formatPaise(total)),
            { title: strings.pos.view.tenderRequiredTitle }
          );
          return;
        }
        if (total > 0 && tendered >= total) {
          amountTenderedInputRef.current?.focus();
          amountTenderedInputRef.current?.select();
          toast.warning(
            strings.pos.toasts.partialPaymentLimit(formatPaise(total)),
            { title: strings.pos.toasts.partialPaymentLimitTitle }
          );
          return;
        }
      }
    }

    if (!allowNegativeStock) {
      for (const item of currentBill.items) {
        const available = productStockById.get(item.productId)?.current_stock;
        if (available != null && item.quantity > available) {
          toast.warning(
            strings.pos.view.insufficientStock(
              item.name,
              available,
              item.quantity
            ),
            { title: strings.pos.view.insufficientStockTitle }
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
      notes: currentBill.notes?.trim() || null,
    };

    saleMutation.mutate(payload);
  };

  const addProductToCart = (product) => {
    if (!allowNegativeStock) {
      const available =
        productStockById.get(product.id)?.current_stock ?? product.current_stock ?? 0;
      const inCart = currentBill.items.find((it) => it.productId === product.id)?.quantity || 0;

      if (available <= 0) {
        toast.warning(strings.pos.view.outOfStock(product.name));
        return;
      }
      if (inCart + 1 > available) {
        toast.warning(
          strings.pos.view.stockLimit(available, product.name),
          { title: strings.pos.view.stockLimitTitle }
        );
        return;
      }
    }
    addItem(product);
  };

  const updateCustomerDetails = async (customer, updates) => {
    if (!customer) return;
    const nextCustomer = { ...customer, ...updates };
    // Update the bill immediately so the checkout payload uses the edited details.
    setCustomer(nextCustomer);
    try {
      const saved = await tauriApi.updateCustomer(customer.id, {
        name: nextCustomer.name,
        phone: nextCustomer.phone || null,
        address: nextCustomer.address || null,
      });
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setCustomer(saved);
    } catch (err) {
      toast.error(strings.pos.view.updateCustomerFailed(err), {
        title: strings.pos.view.updateFailedTitle,
      });
    }
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
          strings.pos.view.stockLimit(available, item.name),
          { title: strings.pos.view.stockLimitTitle }
        );
        return;
      }
    }

    updateItemQty(productId, nextQty);
  };

  const handlePrintReceipt = () => {
    toast.info(strings.pos.view.printDialogOpening);
    window.print();
  };

  const handleClearCart = async () => {
    if (currentBill.items.length === 0) {
      toast.info(strings.pos.view.billAlreadyEmpty(activeBillIndex + 1));
      return;
    }
    const { confirmed } = await confirmDialog({
      title: strings.pos.confirms.clearCart.title,
      message: strings.pos.confirms.clearCart.message(
        currentBill.items.length,
        activeBillIndex + 1
      ),
      confirmLabel: strings.pos.confirms.clearCart.confirmLabel,
      cancelLabel: strings.pos.confirms.clearCart.cancelLabel,
      tone: 'danger',
    });
    if (confirmed) {
      clearCurrentBill();
      toast.info(strings.pos.view.billCleared(activeBillIndex + 1));
    }
  };

  const requiresTender =
    !currentBill.paymentMethod ||
    currentBill.paymentMethod === 'CASH' ||
    currentBill.paymentMethod === 'GIFT_CARD';
  const isFullPayment = currentBill.isFullPayment !== false;
  const tenderedPaise = currentBill.amountTenderedPaise || 0;
  const totalPaise = totals.totalPaise;

  // Amount tendered checks apply to cash and gift card payments; digital methods (UPI, Card) settle the exact amount
  const isPaymentValid = !requiresTender
    ? true
    : isFullPayment
      ? totalPaise === 0 || tenderedPaise >= totalPaise
      : totalPaise > 0 && tenderedPaise > 0 && tenderedPaise < totalPaise;

  const canCompleteSale =
    !saleMutation.isPending &&
    currentBill.items.length > 0 &&
    isPaymentValid;

  const primaryAction = {
    label: strings.pos.view.completeSale,
    pendingLabel: strings.pos.view.processingSale,
    icon: CheckCircle2,
    onClick: handleCompleteSale,
    disabled: !canCompleteSale,
    pending: saleMutation.isPending,
  };

  return (
    <div className="flex-1 flex flex-col h-full overflow-hidden bg-canvas">
      {/* POS Cart and Checkout Editor */}
      <PosBillEditor
        bill={currentBill}
        totals={totals}
        bills={bills}
        activeBillIndex={activeBillIndex}
        onSwitchBill={switchBill}
        onClearCart={handleClearCart}
        products={products}
        customers={customers}
        stockProducts={stockProducts}
        allowNegativeStock={allowNegativeStock}
        currencySymbol={currencySymbol}
        searchQuery={searchQuery}
        onSearchQueryChange={setSearchQuery}
        searchFocused={searchFocused}
        onSearchFocusedChange={setSearchFocused}
        searchInputRef={searchInputRef}
        amountTenderedInputRef={amountTenderedInputRef}
        onAddItem={addProductToCart}
        onUpdateItemQty={changeItemQty}
        onUpdateItemPrice={updateItemPrice}
        onUpdateItemDiscount={updateItemDiscount}
        onRemoveItem={removeItem}
        onSetCustomer={setCustomer}
        onUpdateCustomer={updateCustomerDetails}
        onSetDiscount={setDiscount}
        onSetPaymentMethod={setPaymentMethod}
        onSetFullPayment={setFullPayment}
        onSetAmountTendered={setAmountTendered}
        onRequestAddCustomer={() => setCustomerModalOpen(true)}
        primaryAction={primaryAction}
        notes={currentBill.notes || ''}
        onNotesChange={setNotes}
      />

      {/* ── Completion & Receipt Print Modal ───────────────────────────── */}
      {showPrintModal && completedSale && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-6 shadow-xl space-y-4 animate-scale-in">
            <div className="text-center space-y-2">
              <div className="w-12 h-12 rounded-full bg-success-bg text-success flex items-center justify-center mx-auto">
                <CheckCircle2 className="w-7 h-7" />
              </div>
              <h3 className="text-base font-bold text-text-primary">{strings.pos.view.saleCompletedTitle}</h3>
              <p className="text-xs text-text-muted">
                {strings.pos.view.invoiceNumber}{' '}
                <strong className="text-text-primary font-mono">{completedSale.sale.invoice_number}</strong>
              </p>
            </div>

            {/* Change banner */}
            {completedSale.change_paise > 0 && (
              <div className="bg-surface-secondary p-3.5 rounded-xl border border-border-default text-center">
                <div className="text-[11px] text-text-muted uppercase font-bold tracking-wider">
                  {strings.pos.view.changeToReturn}
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
                onClick={() => setShowPrintModal(false)}
                className="py-2 px-3 rounded-xl bg-surface hover:bg-surface-secondary text-text-primary text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors border border-border-default cursor-pointer"
              >
                <span>{strings.pos.view.newSale}</span>
              </button>
              <button
                type="button"
                onClick={handlePrintReceipt}
                className="py-2 px-3 rounded-xl bg-brand hover:bg-brand-hover text-white text-xs font-bold flex items-center justify-center gap-1.5 transition-colors shadow-2xs cursor-pointer"
              >
                <Printer className="w-4 h-4" />
                <span>{strings.pos.view.printReceipt}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      <CustomerModal
        open={customerModalOpen}
        onClose={() => setCustomerModalOpen(false)}
        onSaved={(saved) => setCustomer(saved)}
      />
    </div>
  );
}
