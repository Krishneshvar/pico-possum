import { useState, useMemo, useRef, useEffect } from 'react';
import { formatCurrency, getCurrencySymbol } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';
import PosCheckoutSidebar from './editor/PosCheckoutSidebar';
import PosCartTable from './editor/PosCartTable';
import { usePosTableNavigation } from './editor/usePosTableNavigation';

const EMPTY_STOCK_ALLOWANCE = new Map();

/**
 * Shared POS bill workspace used both for checkout (PosView) and historical bill amendments.
 * Standardized to 5 primary cart columns, natural checkout sidebar ordering,
 * solid neutral surfaces, and brand-blue interactions.
 */
export default function PosBillEditor({
  bill,
  totals,
  bills = [],
  activeBillIndex = 0,
  onSwitchBill,
  onClearCart,
  products = [],
  customers = [],
  stockProducts = [],
  allowNegativeStock = false,
  stockAllowanceByProduct = EMPTY_STOCK_ALLOWANCE,
  currencySymbol = getCurrencySymbol(),
  searchQuery,
  onSearchQueryChange,
  searchFocused,
  onSearchFocusedChange,
  searchInputRef,
  amountTenderedInputRef,
  onAddItem,
  onUpdateItemQty,
  onUpdateItemPrice,
  onUpdateItemDiscount,
  onRemoveItem,
  onSetCustomer,
  onUpdateCustomer,
  onSetDiscount,
  onSetPaymentMethod,
  onSetFullPayment,
  onSetAmountTendered,
  onRequestAddCustomer,
  primaryAction,
  notes = '',
  onNotesChange,
}) {
  const [selectedProductId, setSelectedProductId] = useState(null);
  const [expandedRowId, setExpandedRowId] = useState(null);
  const [cashTenderedInput, setCashTenderedInput] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [customerAddress, setCustomerAddress] = useState('');
  const cashInputFocusedRef = useRef(false);
  const localAmountTenderedRef = useRef(null);
  const resolvedTenderedRef = amountTenderedInputRef || localAmountTenderedRef;
  const dropdownRef = useRef(null);
  const emptyRowResultRefs = useRef([]);

  const requiresTender =
    !bill.paymentMethod ||
    bill.paymentMethod === 'CASH' ||
    bill.paymentMethod === 'GIFT_CARD';

  // Auto-fill the customer card's phone and address fields from the attached customer
  const activeCustomerId = bill.customer?.id || null;
  useEffect(() => {
    setCustomerPhone(bill.customer?.phone || '');
    setCustomerAddress(bill.customer?.address || '');
  }, [activeCustomerId]);

  const saveCustomerDetails = () => {
    const customer = bill.customer;
    if (!customer || !onUpdateCustomer) return;
    const nextPhone = customerPhone.trim();
    const nextAddress = customerAddress.trim();
    if (nextPhone === (customer.phone || '') && nextAddress === (customer.address || '')) {
      return;
    }
    onUpdateCustomer(customer, {
      phone: nextPhone || null,
      address: nextAddress || null,
    });
  };

  const nav = usePosTableNavigation({
    bill,
    products,
    stockProducts,
    searchInputRef,
    onAddItem,
    onUpdateItemQty,
    onUpdateItemPrice,
    onUpdateItemDiscount,
    onRemoveItem,
    onSearchQueryChange,
    onSearchFocusedChange,
    setSelectedProductId,
    setExpandedRowId,
  });

  const emptyProductRef = nav.emptyProductInputRef;

  // Click outside listener to dismiss search autocomplete dropdown
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target) &&
        emptyProductRef.current &&
        !emptyProductRef.current.contains(e.target)
      ) {
        onSearchFocusedChange(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [onSearchFocusedChange, emptyProductRef]);

  // Keep the amount tendered field in sync with the bill's amountTenderedCents, except while user is typing.
  useEffect(() => {
    if (cashInputFocusedRef.current) return;
    const cents = bill.amountTenderedCents || 0;
    setCashTenderedInput(cents > 0 ? (cents / 100).toFixed(2) : '');
  }, [bill.index, bill.amountTenderedCents]);

  // Partial payment must not take in any amount equal to or greater than the bill amount.
  useEffect(() => {
    if (!requiresTender) return;
    if (bill.isFullPayment === false && (totals.totalCents || 0) > 0 && (bill.amountTenderedCents || 0) >= (totals.totalCents || 0)) {
      onSetAmountTendered(0);
      setCashTenderedInput('');
    }
  }, [requiresTender, bill.isFullPayment, totals.totalCents, bill.amountTenderedCents, onSetAmountTendered]);

  const stockById = useMemo(() => {
    const map = new Map();
    for (const p of stockProducts) map.set(p.id, p);
    return map;
  }, [stockProducts]);

  const availableStockFor = (productId) => {
    const product = stockById.get(productId);
    if (!product) return undefined;
    const current = product.currentStock ?? product.current_stock ?? 0;
    return current + (stockAllowanceByProduct.get(productId) || 0);
  };

  const filteredSearchProducts = products.slice(0, 8);

  return (
    <div className="flex-1 flex overflow-hidden bg-canvas">
      {/* ── Left Area: Cart Table (~66% width) ── */}
      <PosCartTable
        bill={bill}
        totals={totals}
        getWidthStyle={nav.getWidthStyle}
        startResizing={nav.startResizing}
        setColWidths={nav.setColWidths}
        activeCell={nav.activeCell}
        setActiveCell={nav.setActiveCell}
        selectedProductId={selectedProductId}
        setSelectedProductId={setSelectedProductId}
        cellRefs={nav.cellRefs}
        skuInputRefs={nav.skuInputRefs}
        qtyInputRefs={nav.qtyInputRefs}
        unitPriceInputRefs={nav.unitPriceInputRefs}
        discountInputRefs={nav.discountInputRefs}
        availableStockFor={availableStockFor}
        allowNegativeStock={allowNegativeStock}
        handleCellKeyDown={nav.handleCellKeyDown}
        handleQtyKeyDown={nav.handleQtyKeyDown}
        handleUnitPriceKeyDown={nav.handleUnitPriceKeyDown}
        handleDiscountKeyDown={nav.handleDiscountKeyDown}
        onUpdateItemQty={onUpdateItemQty}
        onUpdateItemPrice={onUpdateItemPrice}
        onUpdateItemDiscount={onUpdateItemDiscount}
        priceInputs={nav.priceInputs}
        setPriceInputs={nav.setPriceInputs}
        discountInputs={nav.discountInputs}
        setDiscountInputs={nav.setDiscountInputs}
        emptyRowSkuInputRef={nav.emptyRowSkuInputRef}
        emptyRowSku={nav.emptyRowSku}
        setEmptyRowSku={nav.setEmptyRowSku}
        handleEmptyRowSkuKeyDown={nav.handleEmptyRowSkuKeyDown}
        emptyProductInputRef={emptyProductRef}
        emptyRowQuery={searchQuery}
        setEmptyRowQuery={onSearchQueryChange}
        emptyRowOpen={searchFocused}
        setEmptyRowOpen={onSearchFocusedChange}
        emptyRowResults={filteredSearchProducts}
        emptyRowHighlight={nav.highlightedIndex}
        setEmptyRowHighlight={nav.setHighlightedIndex}
        emptyRowResultRefs={emptyRowResultRefs}
        emptyRowDropdownRef={dropdownRef}
        handleEmptyRowProductKeyDown={(e) =>
          nav.handleEmptyRowProductKeyDown(e, filteredSearchProducts, nav.handleSelectProduct)
        }
        selectEmptyRowProduct={nav.handleSelectProduct}
        currencySymbol={currencySymbol}
        expandedRowId={expandedRowId}
        setExpandedRowId={setExpandedRowId}
      />

      {/* ── Right Checkout Sidebar (32–35%, min 360px) ── */}
      <PosCheckoutSidebar
        bill={bill}
        totals={totals}
        bills={bills}
        activeBillIndex={activeBillIndex}
        onSwitchBill={onSwitchBill}
        onClearCart={onClearCart}
        customers={customers}
        currencySymbol={currencySymbol}
        requiresTender={requiresTender}
        onSetCustomer={onSetCustomer}
        onRequestAddCustomer={onRequestAddCustomer}
        customerPhone={customerPhone}
        setCustomerPhone={setCustomerPhone}
        customerAddress={customerAddress}
        setCustomerAddress={setCustomerAddress}
        saveCustomerDetails={saveCustomerDetails}
        onSetPaymentMethod={onSetPaymentMethod}
        onSetFullPayment={onSetFullPayment}
        onSetAmountTendered={onSetAmountTendered}
        cashTenderedInput={cashTenderedInput}
        setCashTenderedInput={setCashTenderedInput}
        resolvedTenderedRef={resolvedTenderedRef}
        cashInputFocusedRef={cashInputFocusedRef}
        emptyProductInputRef={emptyProductRef}
        onSetDiscount={onSetDiscount}
        notes={notes}
        onNotesChange={onNotesChange}
        primaryAction={primaryAction}
      />
    </div>
  );
}
