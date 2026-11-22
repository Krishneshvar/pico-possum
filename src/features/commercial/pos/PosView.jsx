import { useState, useEffect, useRef, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { tauriApi } from '../../../lib/tauri';
import { usePosStore, computeBillTotals } from '../../../stores/pos.store';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { getCurrencySymbol } from '../../../lib/formatters';
import { validateUnitPriceAgainstMrp } from '../../../lib/validation';
import PosBillEditor from './PosBillEditor';
import CustomerModal from '../customers/CustomerModal';
import SaleCompleteModal from './SaleCompleteModal';
import { strings } from '../../../lib/strings';
import { logger } from '../../../lib/logger';

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
    queryKey: ['billSettings'],
    queryFn: () => tauriApi.getBillSettings(),
  });

  const { data: generalSettings } = useQuery({
    queryKey: ['generalSettings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  // Unfiltered catalog for authoritative stock check
  const { data: stockProducts = [] } = useQuery({
    queryKey: ['products', 'stock-catalog'],
    queryFn: () => tauriApi.getProducts(null, null),
  });

  const allowNegativeStock = generalSettings?.allowNegativeStock ?? generalSettings?.allow_negative_stock ?? false;

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
      queryClient.invalidateQueries({ queryKey: ['saleDetails'] });
      queryClient.invalidateQueries({ queryKey: ['stockMovements'] });
      queryClient.invalidateQueries({ queryKey: ['productFlow'] });
      queryClient.invalidateQueries({ queryKey: ['salesReportSummary'] });
      queryClient.invalidateQueries({ queryKey: ['topProducts'] });
      queryClient.invalidateQueries({ queryKey: ['topCategories'] });
      queryClient.invalidateQueries({ queryKey: ['multiYearSales'] });
      queryClient.invalidateQueries({ queryKey: ['salesBreakdown'] });
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setCompletedSale(response);
      setShowPrintModal(true);
      clearCurrentBill();
      const invNo = response.sale.invoiceNumber ?? response.sale.invoice_number;
      logger.info('POS', `Sale completed: ${invNo}`);
      toast.success(
        strings.pos.view.saleCompletedMessage(invNo),
        { title: strings.pos.view.saleCompletedTitle }
      );
    },
    onError: (err) => {
      logger.error('POS', 'Checkout failed:', err);
      toast.error(strings.pos.view.saleFailed(err), {
        title: strings.pos.view.checkoutFailedTitle,
      });
    },
  });

  useEffect(() => {
    if (showPrintModal) {
      const timer = setTimeout(() => {
        printReceiptBtnRef.current?.focus();
      }, 50);
      return () => clearTimeout(timer);
    }
  }, [showPrintModal]);

  // Keyboard shortcuts listener
  useEffect(() => {
    const handleKeyDown = (e) => {
      const isCmd = e.ctrlKey || e.metaKey;

      if (showPrintModal) {
        if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
          e.preventDefault();
          newSaleBtnRef.current?.focus();
          return;
        }
        if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
          e.preventDefault();
          printReceiptBtnRef.current?.focus();
          return;
        }
      }

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
    const tendered = currentBill.amountTenderedCents || 0;
    const total = totals.totalCents;

    // Amount tendered checks apply to cash and gift card payments; digital methods (UPI, Card) settle the exact amount
    if (requiresTender) {
      if (isFull) {
        if (total > 0 && tendered < total) {
          amountTenderedInputRef.current?.focus();
          amountTenderedInputRef.current?.select();
          toast.info(
            strings.pos.view.enterTenderedAmount(formatCurrency(total)),
            { title: strings.pos.view.tenderRequiredTitle }
          );
          return;
        }
      } else {
        if (tendered <= 0) {
          amountTenderedInputRef.current?.focus();
          amountTenderedInputRef.current?.select();
          toast.info(
            strings.pos.view.enterPartialPayment(formatCurrency(total)),
            { title: strings.pos.view.tenderRequiredTitle }
          );
          return;
        }
        if (total > 0 && tendered >= total) {
          amountTenderedInputRef.current?.focus();
          amountTenderedInputRef.current?.select();
          toast.warning(
            strings.pos.toasts.partialPaymentLimit(formatCurrency(total)),
            { title: strings.pos.toasts.partialPaymentLimitTitle }
          );
          return;
        }
      }
    }

    if (!allowNegativeStock) {
      for (const item of currentBill.items) {
        const available = productStockById.get(item.productId)?.currentStock ?? productStockById.get(item.productId)?.current_stock;
        if (available != null && item.quantity > available) {
          toast.warning(
            strings.pos.view.insufficientStock(item.name, available, item.quantity),
            { title: strings.pos.view.insufficientStockTitle }
          );
          return;
        }
      }
    }

    for (const item of currentBill.items) {
      const priceErr = validateUnitPriceAgainstMrp(item.unitPriceCents, item.mrpCents, item.name);
      if (priceErr) {
        toast.warning(priceErr);
        return;
      }
    }

    const payload = {
      customerId: currentBill.customer?.id || null,
      customerName: currentBill.customer?.name || null,
      customerPhone: currentBill.customer?.phone || null,
      customerAddress: currentBill.customer?.address || null,
      items: currentBill.items.map((it) => ({
        productId: it.productId,
        quantity: it.quantity,
        unitPriceCents: it.unitPriceCents,
        discountCents: it.discountCents || 0,
      })),
      discountCents: totals.discountCents,
      paymentMethod: currentBill.paymentMethod,
      paidCents: totals.tenderedCents,
      notes: currentBill.notes?.trim() || null,
    };

    logger.debug('POS', `Submitting checkout for bill ${activeBillIndex} (${payload.items.length} items)`);
    saleMutation.mutate(payload);
  };

  const addProductToCart = (product) => {
    if (!allowNegativeStock) {
      const current = product.currentStock ?? product.current_stock ?? 0;
      const available =
        (productStockById.get(product.id)?.currentStock ?? productStockById.get(product.id)?.current_stock) ?? current;
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
      const available = productStockById.get(productId)?.currentStock ?? productStockById.get(productId)?.current_stock;
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
  const tenderedCents = currentBill.amountTenderedCents || 0;
  const totalCents = totals.totalCents;

  // Amount tendered checks apply to cash and gift card payments; digital methods (UPI, Card) settle the exact amount
  const isPaymentValid = !requiresTender
    ? true
    : isFullPayment
      ? totalCents === 0 || tenderedCents >= totalCents
      : totalCents > 0 && tenderedCents > 0 && tenderedCents < totalCents;

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
      <SaleCompleteModal
        isOpen={showPrintModal && Boolean(completedSale)}
        onClose={() => setShowPrintModal(false)}
        completedSale={completedSale}
        billSettings={billSettings}
        generalSettings={generalSettings}
        onPrintReceipt={handlePrintReceipt}
      />

      <CustomerModal
        open={customerModalOpen}
        onClose={() => setCustomerModalOpen(false)}
        onSaved={(saved) => setCustomer(saved)}
      />
    </div>
  );
}
