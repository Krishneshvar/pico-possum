import { useState, useMemo, useRef, useEffect } from 'react';
import {
  Search,
  Trash2,
  User,
  X,
  Receipt,
  SlidersHorizontal,
  ChevronDown,
  ChevronUp,
  StickyNote,
} from 'lucide-react';
import { formatPaise, rupeesToPaise } from '../../../lib/formatters';
import Select from '../../../components/common/Select';

const EMPTY_STOCK_ALLOWANCE = new Map();

const POS_TABLE_WIDTHS_KEY = 'pico_possum_pos_table_widths';

const POS_COLUMNS = [
  { key: 'index', label: '#', defaultWidth: 48, minWidth: 36, align: 'center' },
  { key: 'sku', label: 'SKU', defaultWidth: 120, minWidth: 60, align: 'left' },
  { key: 'product', label: 'Product', defaultWidth: 260, minWidth: 140, align: 'left' },
  { key: 'qty', label: 'Qty', defaultWidth: 110, minWidth: 70, align: 'center' },
  { key: 'unitPrice', label: 'Unit Price', defaultWidth: 120, minWidth: 75, align: 'right' },
  { key: 'mrp', label: 'MRP', defaultWidth: 110, minWidth: 70, align: 'right' },
  { key: 'discount', label: 'Discount', defaultWidth: 110, minWidth: 70, align: 'right' },
  { key: 'lineTotal', label: 'Line Total', defaultWidth: 130, minWidth: 80, align: 'right' },
];

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
  currencySymbol = '₹',
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

  // Active cell navigation in the bill table: { row: number, col: number }
  const [activeCell, setActiveCell] = useState({ row: 0, col: 2 });
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const [emptyRowSku, setEmptyRowSku] = useState('');
  const [priceInputs, setPriceInputs] = useState({});
  const [discountInputs, setDiscountInputs] = useState({});

  // ── Column Resizing State (persisted per table) ────────────────────────────
  const [colWidths, setColWidths] = useState(() => {
    try {
      const saved = localStorage.getItem(POS_TABLE_WIDTHS_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (parsed && typeof parsed === 'object') return parsed;
      }
    } catch {
      // ignore
    }
    return {};
  });

  useEffect(() => {
    try {
      localStorage.setItem(POS_TABLE_WIDTHS_KEY, JSON.stringify(colWidths));
    } catch {
      // ignore
    }
  }, [colWidths]);

  const getWidthStyle = (key) => {
    const colDef = POS_COLUMNS.find((c) => c.key === key);
    const width = colWidths[key] ?? colDef?.defaultWidth;
    const minWidth = colDef?.minWidth ?? 40;
    if (width) {
      return { width: `${width}px`, minWidth: `${minWidth}px` };
    }
    return { minWidth: `${minWidth}px` };
  };

  const isResizing = useRef(false);
  const startResizing = (e, colKey) => {
    e.preventDefault();
    e.stopPropagation();
    isResizing.current = true;

    const startX = e.clientX;
    const thEl = e.currentTarget.parentElement;
    const startWidth = thEl ? thEl.getBoundingClientRect().width : (colWidths[colKey] || 100);
    const colDef = POS_COLUMNS.find((c) => c.key === colKey);
    const minW = colDef?.minWidth || 40;

    const onMouseMove = (moveEvent) => {
      const delta = moveEvent.clientX - startX;
      const newWidth = Math.max(minW, Math.round(startWidth + delta));
      setColWidths((prev) => ({
        ...prev,
        [colKey]: newWidth,
      }));
    };

    const onMouseUp = () => {
      isResizing.current = false;
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  };

  const cellRefs = useRef({});
  const qtyInputRefs = useRef({});
  const unitPriceInputRefs = useRef({});
  const discountInputRefs = useRef({});
  const skuInputRefs = useRef({});
  const emptyRowSkuInputRef = useRef(null);
  const emptyProductInputRef = useRef(null);
  const dropdownRef = useRef(null);
  const localAmountTenderedRef = useRef(null);
  const resolvedTenderedRef = amountTenderedInputRef || localAmountTenderedRef;

  // Default focus on empty row's product cell on mount or bill switch
  useEffect(() => {
    setActiveCell({ row: bill.items.length, col: 2 });
    const timer = setTimeout(() => {
      emptyProductInputRef.current?.focus();
    }, 40);
    return () => clearTimeout(timer);
  }, [bill.index]);

  // Click outside listener to dismiss search autocomplete dropdown
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target) &&
        emptyProductInputRef.current &&
        !emptyProductInputRef.current.contains(e.target)
      ) {
        onSearchFocusedChange(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [onSearchFocusedChange]);

  // Handle focusing DOM element when activeCell changes
  useEffect(() => {
    if (!activeCell || activeCell.row < 0 || activeCell.row > bill.items.length) return;
    const { row, col } = activeCell;

    if (row === bill.items.length) {
      if (col === 1) {
        if (emptyRowSkuInputRef.current && document.activeElement !== emptyRowSkuInputRef.current) {
          emptyRowSkuInputRef.current.focus();
        }
        return;
      }
      if (col === 2) {
        if (emptyProductInputRef.current && document.activeElement !== emptyProductInputRef.current) {
          emptyProductInputRef.current.focus();
        }
        return;
      }
    }

    if (row < bill.items.length) {
      if (col === 3) {
        const input = qtyInputRefs.current[row];
        if (input && document.activeElement !== input) {
          input.focus();
          input.select();
        }
        return;
      }
      if (col === 4) {
        const input = unitPriceInputRefs.current[row];
        if (input && document.activeElement !== input) {
          input.focus();
          input.select();
        }
        return;
      }
      if (col === 6) {
        const input = discountInputRefs.current[row];
        if (input && document.activeElement !== input) {
          input.focus();
          input.select();
        }
        return;
      }
    }

    const key = `${row}-${col}`;
    const el = cellRefs.current[key];
    if (el && document.activeElement !== el) {
      el.focus();
      el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
  }, [activeCell, bill.items.length]);

  const findProductBySku = (skuStr) => {
    if (!skuStr) return null;
    const target = skuStr.trim().toLowerCase();
    // Search stockProducts (full catalog)
    let found = stockProducts.find((p) => p.sku && p.sku.trim().toLowerCase() === target);
    if (!found) {
      found = products.find((p) => p.sku && p.sku.trim().toLowerCase() === target);
    }
    if (!found) {
      found = stockProducts.find((p) => p.barcode && p.barcode.trim().toLowerCase() === target);
    }
    if (!found) {
      found = stockProducts.find((p) => p.sku && p.sku.trim().toLowerCase().startsWith(target));
    }
    return found || null;
  };

  const handleSelectProduct = (product) => {
    if (!product) return;
    const existingIdx = bill.items.findIndex((it) => it.productId === product.id);
    const targetIdx = existingIdx >= 0 ? existingIdx : bill.items.length;

    onAddItem(product);
    onSearchQueryChange('');
    setEmptyRowSku('');
    onSearchFocusedChange(false);
    setHighlightedIndex(0);

    // After either the product is selected from the drop-down or by entering the SKU,
    // the focus should move to the quantity
    setTimeout(() => {
      setActiveCell({ row: targetIdx, col: 3 });
      const qtyInput = qtyInputRefs.current[targetIdx];
      if (qtyInput) {
        qtyInput.focus();
        qtyInput.select();
      }
    }, 40);
  };

  // Keyboard navigation handler across table cells
  const handleCellKeyDown = (e, rowIdx, colIdx, item) => {
    const TOTAL_COLS = 8; // # (0), SKU (1), Product (2), Qty (3), Unit Price (4), MRP (5), Discount (6), Line Total (7)
    const maxRow = bill.items.length; // 0..items.length-1 plus empty row at items.length

    if (e.key === 'ArrowRight') {
      e.preventDefault();
      if (colIdx < TOTAL_COLS - 1) {
        setActiveCell({ row: rowIdx, col: colIdx + 1 });
      } else if (rowIdx < maxRow) {
        setActiveCell({ row: rowIdx + 1, col: 0 });
      }
      return;
    }

    if (e.key === 'ArrowLeft') {
      e.preventDefault();
      if (colIdx > 0) {
        setActiveCell({ row: rowIdx, col: colIdx - 1 });
      } else if (rowIdx > 0) {
        setActiveCell({ row: rowIdx - 1, col: TOTAL_COLS - 1 });
      }
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (rowIdx < maxRow) {
        setActiveCell({ row: rowIdx + 1, col: colIdx });
      }
      return;
    }

    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (rowIdx > 0) {
        setActiveCell({ row: rowIdx - 1, col: colIdx });
      }
      return;
    }

    if (e.key === 'Tab') {
      if (e.shiftKey) {
        if (colIdx > 0) {
          e.preventDefault();
          setActiveCell({ row: rowIdx, col: colIdx - 1 });
        } else if (rowIdx > 0) {
          e.preventDefault();
          setActiveCell({ row: rowIdx - 1, col: TOTAL_COLS - 1 });
        }
      } else {
        if (colIdx < TOTAL_COLS - 1) {
          e.preventDefault();
          setActiveCell({ row: rowIdx, col: colIdx + 1 });
        } else if (rowIdx < maxRow) {
          e.preventDefault();
          setActiveCell({ row: rowIdx + 1, col: 0 });
        }
      }
      return;
    }

    if (item && (e.key === 'Delete' || e.key === 'Backspace')) {
      e.preventDefault();
      onRemoveItem(item.productId);
      return;
    }

    if (item && (e.key === '+' || e.key === '=')) {
      e.preventDefault();
      onUpdateItemQty(item.productId, item.quantity + 1);
      return;
    }

    if (item && e.key === '-') {
      e.preventDefault();
      onUpdateItemQty(item.productId, Math.max(1, item.quantity - 1));
      return;
    }

    if (item && e.key === 'Enter') {
      if (colIdx === 4) {
        e.preventDefault();
        setExpandedRowId((prev) => (prev === item.productId ? null : item.productId));
      }
    }
  };

  // Keyboard handler when focused inside Qty input
  const handleQtyKeyDown = (e, rowIdx, item) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      // After entering quantity and hitting enter, move to unit price cell
      setActiveCell({ row: rowIdx, col: 4 });
      setTimeout(() => {
        const upInput = unitPriceInputRefs.current[rowIdx];
        if (upInput) {
          upInput.focus();
          upInput.select();
        }
      }, 30);
      return;
    }

    if (e.key === 'ArrowRight') {
      e.preventDefault();
      setActiveCell({ row: rowIdx, col: 4 });
      unitPriceInputRefs.current[rowIdx]?.focus();
      unitPriceInputRefs.current[rowIdx]?.select();
      return;
    }

    if (e.key === 'ArrowLeft') {
      e.preventDefault();
      setActiveCell({ row: rowIdx, col: 2 });
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (rowIdx < bill.items.length - 1) {
        setActiveCell({ row: rowIdx + 1, col: 3 });
        qtyInputRefs.current[rowIdx + 1]?.focus();
        qtyInputRefs.current[rowIdx + 1]?.select();
      } else {
        setActiveCell({ row: bill.items.length, col: 3 });
      }
      return;
    }

    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (rowIdx > 0) {
        setActiveCell({ row: rowIdx - 1, col: 3 });
        qtyInputRefs.current[rowIdx - 1]?.focus();
        qtyInputRefs.current[rowIdx - 1]?.select();
      }
      return;
    }

    if (e.key === 'Tab') {
      e.preventDefault();
      if (e.shiftKey) {
        setActiveCell({ row: rowIdx, col: 2 });
      } else {
        setActiveCell({ row: rowIdx, col: 4 });
        unitPriceInputRefs.current[rowIdx]?.focus();
        unitPriceInputRefs.current[rowIdx]?.select();
      }
      return;
    }

    if (e.key === '+' || e.key === '=') {
      e.preventDefault();
      onUpdateItemQty(item.productId, item.quantity + 1);
      return;
    }

    if (e.key === '-') {
      e.preventDefault();
      onUpdateItemQty(item.productId, Math.max(1, item.quantity - 1));
      return;
    }

    if (e.key === 'Delete') {
      e.preventDefault();
      onRemoveItem(item.productId);
      return;
    }
  };

  // Keyboard handler when focused inside Unit Price input
  const handleUnitPriceKeyDown = (e, rowIdx, item) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      // After entering the unit price and hitting enter, the next row should appear
      if (rowIdx < bill.items.length - 1) {
        setActiveCell({ row: rowIdx + 1, col: 2 });
      } else {
        setActiveCell({ row: bill.items.length, col: 2 });
        setTimeout(() => {
          emptyProductInputRef.current?.focus();
        }, 30);
      }
      return;
    }

    if (e.key === 'ArrowRight') {
      e.preventDefault();
      setActiveCell({ row: rowIdx, col: 5 });
      return;
    }

    if (e.key === 'ArrowLeft') {
      e.preventDefault();
      setActiveCell({ row: rowIdx, col: 3 });
      qtyInputRefs.current[rowIdx]?.focus();
      qtyInputRefs.current[rowIdx]?.select();
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (rowIdx < bill.items.length - 1) {
        setActiveCell({ row: rowIdx + 1, col: 4 });
        unitPriceInputRefs.current[rowIdx + 1]?.focus();
        unitPriceInputRefs.current[rowIdx + 1]?.select();
      } else {
        setActiveCell({ row: bill.items.length, col: 4 });
      }
      return;
    }

    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (rowIdx > 0) {
        setActiveCell({ row: rowIdx - 1, col: 4 });
        unitPriceInputRefs.current[rowIdx - 1]?.focus();
        unitPriceInputRefs.current[rowIdx - 1]?.select();
      }
      return;
    }

    if (e.key === 'Tab') {
      e.preventDefault();
      if (e.shiftKey) {
        setActiveCell({ row: rowIdx, col: 3 });
        qtyInputRefs.current[rowIdx]?.focus();
        qtyInputRefs.current[rowIdx]?.select();
      } else {
        if (rowIdx < bill.items.length - 1) {
          setActiveCell({ row: rowIdx + 1, col: 0 });
        } else {
          setActiveCell({ row: bill.items.length, col: 2 });
          emptyProductInputRef.current?.focus();
        }
      }
      return;
    }

    if (e.key === 'Delete') {
      e.preventDefault();
      onRemoveItem(item.productId);
      return;
    }
  };

  // Keyboard handler when focused inside Discount input
  const handleDiscountKeyDown = (e, rowIdx, item) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (rowIdx < bill.items.length - 1) {
        setActiveCell({ row: rowIdx + 1, col: 2 });
      } else {
        setActiveCell({ row: bill.items.length, col: 2 });
        setTimeout(() => {
          emptyProductInputRef.current?.focus();
        }, 30);
      }
      return;
    }

    if (e.key === 'ArrowRight') {
      e.preventDefault();
      setActiveCell({ row: rowIdx, col: 7 }); // Line Total
      return;
    }

    if (e.key === 'ArrowLeft') {
      e.preventDefault();
      setActiveCell({ row: rowIdx, col: 5 }); // MRP
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (rowIdx < bill.items.length - 1) {
        setActiveCell({ row: rowIdx + 1, col: 6 });
        discountInputRefs.current[rowIdx + 1]?.focus();
        discountInputRefs.current[rowIdx + 1]?.select();
      } else {
        setActiveCell({ row: bill.items.length, col: 6 });
      }
      return;
    }

    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (rowIdx > 0) {
        setActiveCell({ row: rowIdx - 1, col: 6 });
        discountInputRefs.current[rowIdx - 1]?.focus();
        discountInputRefs.current[rowIdx - 1]?.select();
      }
      return;
    }

    if (e.key === 'Tab') {
      e.preventDefault();
      if (e.shiftKey) {
        setActiveCell({ row: rowIdx, col: 5 }); // MRP
      } else {
        setActiveCell({ row: rowIdx, col: 7 }); // Line Total
      }
      return;
    }

    if (e.key === 'Delete') {
      e.preventDefault();
      onRemoveItem(item.productId);
      return;
    }
  };

  // Keyboard handler when focused inside the default empty row's SKU input
  const handleEmptyRowSkuKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (!emptyRowSku.trim()) return;
      const found = findProductBySku(emptyRowSku);
      if (found) {
        handleSelectProduct(found);
      } else {
        toast.warning(`No product found with SKU "${emptyRowSku.trim()}"`, {
          title: 'SKU Not Found',
        });
      }
      return;
    }

    if (e.key === 'ArrowRight' && (!emptyRowSku || e.target.selectionStart === emptyRowSku.length)) {
      e.preventDefault();
      setActiveCell({ row: bill.items.length, col: 2 });
      setTimeout(() => {
        emptyProductInputRef.current?.focus();
      }, 30);
      return;
    }

    if (e.key === 'ArrowLeft' && (!emptyRowSku || e.target.selectionStart === 0)) {
      e.preventDefault();
      setActiveCell({ row: bill.items.length, col: 0 });
      return;
    }

    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (bill.items.length > 0) {
        setActiveCell({ row: bill.items.length - 1, col: 1 });
      }
      return;
    }

    if (e.key === 'Tab') {
      e.preventDefault();
      if (e.shiftKey) {
        setActiveCell({ row: bill.items.length, col: 0 });
      } else {
        setActiveCell({ row: bill.items.length, col: 2 });
        setTimeout(() => {
          emptyProductInputRef.current?.focus();
        }, 30);
      }
      return;
    }
  };

  // Keyboard handler when focused inside the default empty row's Product search input
  const handleEmptyRowProductKeyDown = (e) => {
    const hasDropdown = searchFocused && searchQuery && filteredSearchProducts.length > 0;

    if (e.key === 'ArrowDown') {
      if (hasDropdown) {
        e.preventDefault();
        setHighlightedIndex((prev) => (prev + 1) % filteredSearchProducts.length);
        return;
      }
      return;
    }

    if (e.key === 'ArrowUp') {
      if (hasDropdown && highlightedIndex > 0) {
        e.preventDefault();
        setHighlightedIndex((prev) => prev - 1);
        return;
      }
      // Navigate to previous committed row's Product cell
      if (bill.items.length > 0) {
        e.preventDefault();
        onSearchFocusedChange(false);
        setActiveCell({ row: bill.items.length - 1, col: 2 });
      }
      return;
    }

    if (e.key === 'Enter') {
      e.preventDefault();
      if (filteredSearchProducts.length > 0) {
        const trimmed = searchQuery.trim().toLowerCase();
        const exactMatch = filteredSearchProducts.find(
          (p) =>
            (p.sku && p.sku.toLowerCase() === trimmed) ||
            p.name.toLowerCase() === trimmed
        );
        const chosen =
          exactMatch || filteredSearchProducts[highlightedIndex] || filteredSearchProducts[0];
        handleSelectProduct(chosen);
      }
      return;
    }

    if (e.key === 'Escape') {
      e.preventDefault();
      onSearchQueryChange('');
      onSearchFocusedChange(false);
      return;
    }

    if (e.key === 'ArrowLeft' && (!searchQuery || e.target.selectionStart === 0)) {
      e.preventDefault();
      setActiveCell({ row: bill.items.length, col: 1 });
      setTimeout(() => {
        emptyRowSkuInputRef.current?.focus();
        emptyRowSkuInputRef.current?.select();
      }, 30);
      return;
    }

    if (e.key === 'ArrowRight' && (!searchQuery || e.target.selectionStart === searchQuery.length)) {
      e.preventDefault();
      setActiveCell({ row: bill.items.length, col: 3 });
      return;
    }

    if (e.key === 'Tab') {
      if (e.shiftKey) {
        e.preventDefault();
        setActiveCell({ row: bill.items.length, col: 1 });
        emptyRowSkuInputRef.current?.focus();
      } else {
        e.preventDefault();
        setActiveCell({ row: bill.items.length, col: 3 });
      }
      return;
    }
  };

  // Keep the amount tendered field in sync with the bill's amountTenderedPaise, except while user is typing.
  // Amount tendered starts at 0 and does not auto-increase with the bill amount.
  useEffect(() => {
    if (cashInputFocusedRef.current) return;
    const paise = bill.amountTenderedPaise || 0;
    setCashTenderedInput(paise > 0 ? (paise / 100).toFixed(2) : '');
  }, [bill.index, bill.amountTenderedPaise]);

  // Partial payment must not take in any amount equal to or greater than the bill amount.
  // If bill total decreases below current tendered amount while in partial mode, reset to 0.
  useEffect(() => {
    if (!requiresTender) return;
    if (bill.isFullPayment === false && totals.totalPaise > 0 && (bill.amountTenderedPaise || 0) >= totals.totalPaise) {
      onSetAmountTendered(0);
      setCashTenderedInput('');
    }
  }, [requiresTender, bill.isFullPayment, totals.totalPaise, bill.amountTenderedPaise, onSetAmountTendered]);

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

  return (
    <div className="flex-1 flex overflow-hidden bg-canvas">
      {/* ── Left Area: Cart Table (~66% width) ── */}
      <div className="flex-1 flex flex-col border-r border-border-default overflow-hidden bg-surface">
        {/* Grand Total pinned above the table headers */}
        <div className="shrink-0 flex items-center justify-between px-4 py-2 bg-surface border-b border-border-default">
          <span className="text-[11px] font-bold text-text-muted uppercase tracking-wider">
            Grand Total
          </span>
          <span className="text-2xl font-extrabold text-text-primary financial-value">
            {formatPaise(totals.totalPaise)}
          </span>
        </div>

        {/* Cart Items Table: extends flush to edges with actual table look, default empty row, and arrow key cell navigation */}
        <div className="flex-1 overflow-auto bg-surface flex flex-col">
          <div className="flex-1 overflow-auto">
            <table className="w-full text-left border-collapse border-b border-border-default select-text">
              <thead className="bg-table-header-bg text-[11px] font-semibold text-table-header-text uppercase tracking-wider sticky top-0 border-b border-table-header-border z-10 shadow-xs">
                <tr className="h-9">
                  {POS_COLUMNS.map((col, cIdx) => {
                    const isLast = cIdx === POS_COLUMNS.length - 1;
                    return (
                      <th
                        key={col.key}
                        style={getWidthStyle(col.key)}
                        className={`py-2.5 px-3 relative select-none ${
                          col.align === 'center'
                            ? 'text-center'
                            : col.align === 'right'
                            ? 'text-right'
                            : 'text-left'
                        } ${!isLast ? 'border-r border-table-header-border' : ''}`}
                      >
                        <span className="truncate">{col.label}</span>
                        {/* Resize Handle */}
                        <div
                          onMouseDown={(e) => startResizing(e, col.key)}
                          onClick={(e) => e.stopPropagation()}
                          onDoubleClick={(e) => {
                            e.stopPropagation();
                            setColWidths((p) => {
                              const next = { ...p };
                              delete next[col.key];
                              return next;
                            });
                          }}
                          title="Drag to resize, double-click to reset"
                          className="absolute top-0 right-0 bottom-0 w-3 -mr-1.5 cursor-col-resize select-none flex items-center justify-center group/resize z-20"
                        >
                          <div className="w-[1.5px] h-3.5 bg-border-strong group-hover/resize:bg-brand group-hover/resize:w-[2px] group-hover/resize:h-full transition-all" />
                        </div>
                      </th>
                    );
                  })}
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
                  const isRowActive = isSelected || activeCell?.row === index;

                  return (
                    <tr
                      key={item.productId}
                      onClick={() => setSelectedProductId(item.productId)}
                      className={`transition-colors border-b border-border-default ${
                        isRowActive
                          ? 'bg-brand-subtle/20'
                          : index % 2 === 1
                          ? 'bg-surface-secondary/25 hover:bg-surface-secondary/50'
                          : 'bg-surface hover:bg-surface-secondary/50'
                      }`}
                    >
                      {/* 1. # */}
                      <td
                        tabIndex={0}
                        ref={(el) => {
                          cellRefs.current[`${index}-0`] = el;
                        }}
                        style={getWidthStyle('index')}
                        onClick={() => {
                          setActiveCell({ row: index, col: 0 });
                          setSelectedProductId(item.productId);
                        }}
                        onFocus={() => {
                          setActiveCell({ row: index, col: 0 });
                          setSelectedProductId(item.productId);
                        }}
                        onKeyDown={(e) => handleCellKeyDown(e, index, 0, item)}
                        className={`py-2 px-3 text-center text-text-muted font-mono text-[11px] border-r border-border-default relative outline-none transition-colors ${
                          activeCell?.row === index && activeCell?.col === 0
                            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                            : ''
                        }`}
                      >
                        {index + 1}
                      </td>

                      {/* 2. SKU */}
                      <td
                        tabIndex={0}
                        ref={(el) => {
                          cellRefs.current[`${index}-1`] = el;
                        }}
                        style={getWidthStyle('sku')}
                        onClick={() => {
                          setActiveCell({ row: index, col: 1 });
                          setSelectedProductId(item.productId);
                        }}
                        onFocus={() => {
                          setActiveCell({ row: index, col: 1 });
                          setSelectedProductId(item.productId);
                        }}
                        onKeyDown={(e) => handleCellKeyDown(e, index, 1, item)}
                        className={`py-1.5 px-2 font-mono text-[11px] text-text-secondary border-r border-border-default whitespace-nowrap relative outline-none transition-colors ${
                          activeCell?.row === index && activeCell?.col === 1
                            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                            : ''
                        }`}
                      >
                        <input
                          ref={(el) => {
                            skuInputRefs.current[index] = el;
                          }}
                          type="text"
                          readOnly
                          value={item.sku || ''}
                          onFocus={() => {
                            setActiveCell({ row: index, col: 1 });
                            setSelectedProductId(item.productId);
                          }}
                          className="w-full bg-transparent font-mono text-[11px] text-text-secondary focus:outline-none cursor-default"
                        />
                      </td>

                      {/* 3. Product */}
                      <td
                        tabIndex={0}
                        ref={(el) => {
                          cellRefs.current[`${index}-2`] = el;
                        }}
                        style={getWidthStyle('product')}
                        onClick={() => {
                          setActiveCell({ row: index, col: 2 });
                          setSelectedProductId(item.productId);
                        }}
                        onFocus={() => {
                          setActiveCell({ row: index, col: 2 });
                          setSelectedProductId(item.productId);
                        }}
                        onKeyDown={(e) => handleCellKeyDown(e, index, 2, item)}
                        className={`py-2 px-3.5 border-r border-border-default relative outline-none transition-colors ${
                          activeCell?.row === index && activeCell?.col === 2
                            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                            : ''
                        }`}
                      >
                        <div className="font-semibold text-text-primary text-xs leading-tight">
                          {item.name}
                        </div>
                        {(item.discountPaise > 0 || exceedsStock) && (
                          <div className="text-[10px] flex items-center gap-2 mt-0.5 font-sans">
                            {item.discountPaise > 0 && (
                              <span className="text-amber-600 dark:text-amber-400 font-medium">
                                Disc: -{formatPaise(item.discountPaise)}
                              </span>
                            )}
                            {exceedsStock && (
                              <span className="text-danger font-semibold">
                                Only {availableStock} in stock
                              </span>
                            )}
                          </div>
                        )}
                      </td>

                      {/* 4. Qty */}
                      <td
                        style={getWidthStyle('qty')}
                        onClick={() => {
                          setActiveCell({ row: index, col: 3 });
                          setSelectedProductId(item.productId);
                        }}
                        className={`py-1.5 px-2 text-center border-r border-border-default relative outline-none transition-colors ${
                          activeCell?.row === index && activeCell?.col === 3
                            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                            : ''
                        }`}
                      >
                        <input
                          ref={(el) => {
                            qtyInputRefs.current[index] = el;
                          }}
                          type="number"
                          min="1"
                          value={item.quantity}
                          onClick={(e) => e.stopPropagation()}
                          onFocus={() => {
                            setActiveCell({ row: index, col: 3 });
                            setSelectedProductId(item.productId);
                          }}
                          onChange={(e) => {
                            const val = parseInt(e.target.value, 10);
                            onUpdateItemQty(item.productId, isNaN(val) ? 1 : val);
                          }}
                          onKeyDown={(e) => handleQtyKeyDown(e, index, item)}
                          className="w-20 text-center bg-surface-secondary border border-border-default hover:border-border-strong focus:border-brand focus:bg-surface focus:outline-none rounded px-2 py-1 font-mono font-semibold text-xs text-text-primary transition-colors"
                        />
                      </td>

                      {/* 5. Unit Price */}
                      <td
                        tabIndex={-1}
                        style={getWidthStyle('unitPrice')}
                        onClick={() => {
                          setActiveCell({ row: index, col: 4 });
                          setSelectedProductId(item.productId);
                          unitPriceInputRefs.current[index]?.focus();
                          unitPriceInputRefs.current[index]?.select();
                        }}
                        className={`py-1.5 px-2 text-right border-r border-border-default relative outline-none transition-colors ${
                          activeCell?.row === index && activeCell?.col === 4
                            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                            : ''
                        }`}
                      >
                        <div className="flex items-center justify-end">
                          <input
                            ref={(el) => {
                              unitPriceInputRefs.current[index] = el;
                            }}
                            type="number"
                            step="0.01"
                            min="0"
                            max={item.mrpPaise ? (item.mrpPaise / 100).toFixed(2) : undefined}
                            value={
                              priceInputs[item.productId] !== undefined
                                ? priceInputs[item.productId]
                                : (item.unitPricePaise / 100).toFixed(2)
                            }
                            onFocus={() => {
                              setActiveCell({ row: index, col: 4 });
                              setSelectedProductId(item.productId);
                            }}
                            onChange={(e) => {
                              const val = e.target.value;
                              const mrpPaise = item.mrpPaise ?? item.unitPricePaise;
                              const parsedPaise = rupeesToPaise(val);
                              if (mrpPaise != null && parsedPaise > mrpPaise) {
                                toast.warning(
                                  `Unit price cannot exceed MRP (${formatPaise(mrpPaise)}).`,
                                  { title: 'Price Exceeds MRP' }
                                );
                                const cappedVal = (mrpPaise / 100).toFixed(2);
                                setPriceInputs((prev) => ({ ...prev, [item.productId]: cappedVal }));
                                onUpdateItemPrice(item.productId, mrpPaise);
                                return;
                              }
                              setPriceInputs((prev) => ({ ...prev, [item.productId]: val }));
                              onUpdateItemPrice(item.productId, parsedPaise);
                            }}
                            onBlur={() => {
                              setPriceInputs((prev) => {
                                const next = { ...prev };
                                delete next[item.productId];
                                return next;
                              });
                              const mrpPaise = item.mrpPaise ?? item.unitPricePaise;
                              if (mrpPaise != null && item.unitPricePaise > mrpPaise) {
                                onUpdateItemPrice(item.productId, mrpPaise);
                              }
                            }}
                            onKeyDown={(e) => handleUnitPriceKeyDown(e, index, item)}
                            className="w-24 text-right bg-surface-secondary/50 border border-border-default hover:border-border-strong focus:border-brand focus:bg-surface focus:outline-none rounded px-2 py-1 font-mono font-medium text-xs text-text-primary transition-colors"
                          />
                        </div>
                      </td>

                      {/* 6. MRP */}
                      <td
                        tabIndex={0}
                        ref={(el) => {
                          cellRefs.current[`${index}-5`] = el;
                        }}
                        style={getWidthStyle('mrp')}
                        onClick={() => {
                          setActiveCell({ row: index, col: 5 });
                          setSelectedProductId(item.productId);
                        }}
                        onFocus={() => {
                          setActiveCell({ row: index, col: 5 });
                          setSelectedProductId(item.productId);
                        }}
                        onKeyDown={(e) => handleCellKeyDown(e, index, 5, item)}
                        className={`py-2 px-3 text-right border-r border-border-default relative outline-none transition-colors ${
                          activeCell?.row === index && activeCell?.col === 5
                            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                            : ''
                        }`}
                      >
                        <span
                          className={`financial-value font-mono text-xs ${
                            (item.mrpPaise ?? item.unitPricePaise) > item.unitPricePaise
                              ? 'line-through text-text-muted/80'
                              : 'text-text-muted'
                          }`}
                        >
                          {formatPaise(item.mrpPaise ?? item.unitPricePaise)}
                        </span>
                      </td>

                      {/* 7. Discount */}
                      <td
                        tabIndex={-1}
                        style={getWidthStyle('discount')}
                        onClick={() => {
                          setActiveCell({ row: index, col: 6 });
                          setSelectedProductId(item.productId);
                          discountInputRefs.current[index]?.focus();
                          discountInputRefs.current[index]?.select();
                        }}
                        className={`py-1.5 px-2 text-right border-r border-border-default relative outline-none transition-colors ${
                          activeCell?.row === index && activeCell?.col === 6
                            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                            : ''
                        }`}
                      >
                        <div className="flex items-center justify-end">
                          <input
                            ref={(el) => {
                              discountInputRefs.current[index] = el;
                            }}
                            type="number"
                            step="0.01"
                            min="0"
                            placeholder="0.00"
                            value={
                              discountInputs[item.productId] !== undefined
                                ? discountInputs[item.productId]
                                : item.discountPaise > 0
                                ? (item.discountPaise / 100).toFixed(2)
                                : ''
                            }
                            onFocus={() => {
                              setActiveCell({ row: index, col: 6 });
                              setSelectedProductId(item.productId);
                            }}
                            onChange={(e) => {
                              const val = e.target.value;
                              setDiscountInputs((prev) => ({ ...prev, [item.productId]: val }));
                              onUpdateItemDiscount(item.productId, rupeesToPaise(val));
                            }}
                            onBlur={() => {
                              setDiscountInputs((prev) => {
                                const next = { ...prev };
                                delete next[item.productId];
                                return next;
                              });
                            }}
                            onKeyDown={(e) => handleDiscountKeyDown(e, index, item)}
                            className="w-20 text-right bg-surface-secondary/50 border border-border-default hover:border-border-strong focus:border-brand focus:bg-surface focus:outline-none rounded px-2 py-1 font-mono font-medium text-xs text-text-primary placeholder:text-text-muted/50 transition-colors"
                          />
                        </div>
                      </td>

                      {/* 8. Line Total */}
                      <td
                        tabIndex={0}
                        ref={(el) => {
                          cellRefs.current[`${index}-7`] = el;
                        }}
                        style={getWidthStyle('lineTotal')}
                        onClick={() => {
                          setActiveCell({ row: index, col: 7 });
                          setSelectedProductId(item.productId);
                        }}
                        onFocus={() => {
                          setActiveCell({ row: index, col: 7 });
                          setSelectedProductId(item.productId);
                        }}
                        onKeyDown={(e) => handleCellKeyDown(e, index, 7, item)}
                        className={`py-2 px-3 text-right border-r-0 relative outline-none transition-colors ${
                          activeCell?.row === index && activeCell?.col === 7
                            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                            : ''
                        }`}
                      >
                        <span className="financial-value font-mono font-bold text-text-primary text-xs">
                          {formatPaise(lineTotal)}
                        </span>
                      </td>
                    </tr>
                  );
                })}

                {/* Default Empty Row (always present at bottom of bill for typing/scanning) */}
                <tr
                  className={`transition-colors border-b border-border-default ${
                    activeCell?.row === bill.items.length
                      ? 'bg-brand-subtle/10'
                      : bill.items.length % 2 === 1
                      ? 'bg-surface-secondary/25'
                      : 'bg-surface'
                  }`}
                >
                  {/* 1. # */}
                  <td
                    tabIndex={0}
                    ref={(el) => {
                      cellRefs.current[`${bill.items.length}-0`] = el;
                    }}
                    style={getWidthStyle('index')}
                    onClick={() => {
                      setActiveCell({ row: bill.items.length, col: 0 });
                    }}
                    onFocus={() => {
                      setActiveCell({ row: bill.items.length, col: 0 });
                    }}
                    onKeyDown={(e) => handleCellKeyDown(e, bill.items.length, 0, null)}
                    className={`py-2 px-3 text-center text-text-muted font-mono text-[11px] border-r border-border-default relative outline-none transition-colors ${
                      activeCell?.row === bill.items.length && activeCell?.col === 0
                        ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                        : ''
                    }`}
                  >
                    {bill.items.length + 1}
                  </td>

                  {/* 2. SKU (Input) */}
                  <td
                    tabIndex={-1}
                    style={getWidthStyle('sku')}
                    onClick={() => {
                      setActiveCell({ row: bill.items.length, col: 1 });
                      emptyRowSkuInputRef.current?.focus();
                      emptyRowSkuInputRef.current?.select();
                    }}
                    className={`p-1.5 font-mono text-[11px] border-r border-border-default whitespace-nowrap relative outline-none transition-colors ${
                      activeCell?.row === bill.items.length && activeCell?.col === 1
                        ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                        : ''
                    }`}
                  >
                    <input
                      ref={(el) => {
                        emptyRowSkuInputRef.current = el;
                        cellRefs.current[`${bill.items.length}-1`] = el;
                      }}
                      type="text"
                      placeholder=""
                      value={emptyRowSku}
                      onChange={(e) => setEmptyRowSku(e.target.value)}
                      onFocus={() => {
                        setActiveCell({ row: bill.items.length, col: 1 });
                      }}
                      onKeyDown={handleEmptyRowSkuKeyDown}
                      className="w-full px-2 py-1.5 rounded-lg bg-surface-secondary/70 border border-border-strong text-xs font-mono text-text-primary placeholder:text-text-muted/70 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
                    />
                  </td>

                  {/* 3. Product (Search & Input cell with Autocomplete) */}
                  <td
                    style={getWidthStyle('product')}
                    className={`p-1.5 border-r border-border-default relative ${
                      activeCell?.row === bill.items.length && activeCell?.col === 2
                        ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-20'
                        : ''
                    }`}
                  >
                    <div className="relative w-full">
                      <div className="relative flex items-center">
                        <Search className="w-3.5 h-3.5 absolute left-2.5 text-text-muted pointer-events-none" />
                        <input
                          ref={(el) => {
                            emptyProductInputRef.current = el;
                            if (searchInputRef) searchInputRef.current = el;
                          }}
                          type="text"
                          placeholder="Search... (Ctrl+K)"
                          value={searchQuery}
                          onChange={(e) => {
                            onSearchQueryChange(e.target.value);
                            setHighlightedIndex(0);
                            if (!searchFocused) onSearchFocusedChange(true);
                          }}
                          onFocus={() => {
                            setActiveCell({ row: bill.items.length, col: 2 });
                            onSearchFocusedChange(true);
                          }}
                          onKeyDown={handleEmptyRowProductKeyDown}
                          className="w-full pl-8 pr-16 py-1.5 rounded-lg bg-surface-secondary/70 border border-border-strong text-xs text-text-primary placeholder:text-text-muted/70 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand font-medium transition-colors"
                        />
                        {searchQuery && (
                          <span className="absolute right-2 text-[10px] text-text-muted font-mono bg-surface px-1.5 py-0.5 rounded border border-border-default pointer-events-none">
                            ↵ Enter
                          </span>
                        )}
                      </div>

                      {/* Autocomplete Dropdown floating below the cell */}
                      {searchFocused && searchQuery && (
                        <div
                          ref={dropdownRef}
                          className="absolute left-0 top-full mt-1 min-w-[280px] w-full max-w-lg bg-surface border border-border-default rounded-xl shadow-2xl z-50 overflow-hidden divide-y divide-border-default max-h-64 overflow-y-auto animate-scale-in"
                        >
                          {filteredSearchProducts.length > 0 ? (
                            filteredSearchProducts.map((p, idx) => {
                              const isHighlighted = idx === highlightedIndex;

                              return (
                                <button
                                  key={p.id}
                                  type="button"
                                  onClick={() => handleSelectProduct(p)}
                                  onMouseEnter={() => setHighlightedIndex(idx)}
                                  className={`w-full flex items-center px-3.5 py-2 text-left text-xs transition-colors cursor-pointer ${
                                    isHighlighted
                                      ? 'bg-brand text-white font-medium'
                                      : 'text-text-primary hover:bg-surface-secondary'
                                  }`}
                                >
                                  <span className="truncate">{p.name}</span>
                                </button>
                              );
                            })
                          ) : (
                            <div className="p-3.5 text-center text-xs text-text-muted">
                              No products matching "{searchQuery}"
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </td>

                  {/* 4. Qty */}
                  <td
                    tabIndex={0}
                    ref={(el) => {
                      cellRefs.current[`${bill.items.length}-3`] = el;
                    }}
                    style={getWidthStyle('qty')}
                    onClick={() => {
                      setActiveCell({ row: bill.items.length, col: 3 });
                    }}
                    onFocus={() => {
                      setActiveCell({ row: bill.items.length, col: 3 });
                    }}
                    onKeyDown={(e) => handleCellKeyDown(e, bill.items.length, 3, null)}
                    className={`py-2 px-2 text-center text-text-muted font-mono text-xs border-r border-border-default relative outline-none transition-colors ${
                      activeCell?.row === bill.items.length && activeCell?.col === 3
                        ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                        : ''
                    }`}
                  >
                    —
                  </td>

                  {/* 5. Unit Price */}
                  <td
                    tabIndex={0}
                    ref={(el) => {
                      cellRefs.current[`${bill.items.length}-4`] = el;
                    }}
                    style={getWidthStyle('unitPrice')}
                    onClick={() => {
                      setActiveCell({ row: bill.items.length, col: 4 });
                    }}
                    onFocus={() => {
                      setActiveCell({ row: bill.items.length, col: 4 });
                    }}
                    onKeyDown={(e) => handleCellKeyDown(e, bill.items.length, 4, null)}
                    className={`py-2 px-3 text-right text-text-muted font-mono text-xs border-r border-border-default relative outline-none transition-colors ${
                      activeCell?.row === bill.items.length && activeCell?.col === 4
                        ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                        : ''
                    }`}
                  >
                    —
                  </td>

                  {/* 6. MRP */}
                  <td
                    tabIndex={0}
                    ref={(el) => {
                      cellRefs.current[`${bill.items.length}-5`] = el;
                    }}
                    style={getWidthStyle('mrp')}
                    onClick={() => {
                      setActiveCell({ row: bill.items.length, col: 5 });
                    }}
                    onFocus={() => {
                      setActiveCell({ row: bill.items.length, col: 5 });
                    }}
                    onKeyDown={(e) => handleCellKeyDown(e, bill.items.length, 5, null)}
                    className={`py-2 px-3 text-right text-text-muted font-mono text-xs border-r border-border-default relative outline-none transition-colors ${
                      activeCell?.row === bill.items.length && activeCell?.col === 5
                        ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                        : ''
                    }`}
                  >
                    —
                  </td>

                  {/* 7. Discount */}
                  <td
                    tabIndex={0}
                    ref={(el) => {
                      cellRefs.current[`${bill.items.length}-6`] = el;
                    }}
                    style={getWidthStyle('discount')}
                    onClick={() => {
                      setActiveCell({ row: bill.items.length, col: 6 });
                    }}
                    onFocus={() => {
                      setActiveCell({ row: bill.items.length, col: 6 });
                    }}
                    onKeyDown={(e) => handleCellKeyDown(e, bill.items.length, 6, null)}
                    className={`py-2 px-3 text-right text-text-muted font-mono text-xs border-r border-border-default relative outline-none transition-colors ${
                      activeCell?.row === bill.items.length && activeCell?.col === 6
                        ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                        : ''
                    }`}
                  >
                    —
                  </td>

                  {/* 8. Line Total */}
                  <td
                    tabIndex={0}
                    ref={(el) => {
                      cellRefs.current[`${bill.items.length}-7`] = el;
                    }}
                    style={getWidthStyle('lineTotal')}
                    onClick={() => {
                      setActiveCell({ row: bill.items.length, col: 7 });
                    }}
                    onFocus={() => {
                      setActiveCell({ row: bill.items.length, col: 7 });
                    }}
                    onKeyDown={(e) => handleCellKeyDown(e, bill.items.length, 7, null)}
                    className={`py-2 px-3 text-right text-text-muted font-mono text-xs border-r-0 relative outline-none transition-colors ${
                      activeCell?.row === bill.items.length && activeCell?.col === 7
                        ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
                        : ''
                    }`}
                  >
                    —
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* Row Edit Drawer if a row is expanded */}
          {expandedRowId && (
            <div className="bg-surface-secondary border-t border-border-default px-4 py-2.5 flex items-center gap-4 text-xs shadow-inner shrink-0">
              <span className="font-semibold text-text-primary">
                Edit Line: {bill.items.find((it) => it.productId === expandedRowId)?.name}
              </span>
              <div className="flex items-center gap-2">
                <span className="text-text-muted">Unit Rate (₹):</span>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  max={
                    (bill.items.find((it) => it.productId === expandedRowId)?.mrpPaise || 0) > 0
                      ? (
                          bill.items.find((it) => it.productId === expandedRowId).mrpPaise / 100
                        ).toFixed(2)
                      : undefined
                  }
                  value={(
                    (bill.items.find((it) => it.productId === expandedRowId)?.unitPricePaise || 0) / 100
                  ).toFixed(2)}
                  onChange={(e) => {
                    const rowItem = bill.items.find((it) => it.productId === expandedRowId);
                    const mrpPaise = rowItem?.mrpPaise ?? rowItem?.unitPricePaise;
                    const parsed = rupeesToPaise(e.target.value);
                    if (mrpPaise != null && parsed > mrpPaise) {
                      toast.warning(
                        `Unit price cannot exceed MRP (${formatPaise(mrpPaise)}).`,
                        { title: 'Price Exceeds MRP' }
                      );
                      onUpdateItemPrice(expandedRowId, mrpPaise);
                      return;
                    }
                    onUpdateItemPrice(expandedRowId, parsed);
                  }}
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
              <div className="bg-surface-secondary p-2 rounded-lg border border-border-default text-xs space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="font-semibold text-text-primary min-w-0 truncate">
                    {bill.customer.name}
                  </div>
                  <button
                    type="button"
                    onClick={() => onSetCustomer(null)}
                    className="text-text-muted hover:text-danger cursor-pointer p-1 shrink-0"
                    title="Remove customer"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>

                <div className="space-y-1.5">
                  <label className="block">
                    <span className="text-[10px] font-semibold uppercase tracking-wider text-text-muted">
                      Phone
                    </span>
                    <input
                      type="tel"
                      value={customerPhone}
                      onChange={(e) => setCustomerPhone(e.target.value)}
                      onBlur={saveCustomerDetails}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') e.currentTarget.blur();
                      }}
                      placeholder="Phone number"
                      className="mt-0.5 w-full bg-surface border border-border-default rounded-md px-2 py-1 text-[11px] font-mono text-text-primary placeholder:text-text-muted/70 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
                    />
                  </label>
                  <label className="block">
                    <span className="text-[10px] font-semibold uppercase tracking-wider text-text-muted">
                      Address
                    </span>
                    <input
                      type="text"
                      value={customerAddress}
                      onChange={(e) => setCustomerAddress(e.target.value)}
                      onBlur={saveCustomerDetails}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') e.currentTarget.blur();
                      }}
                      placeholder="Address / Notes"
                      className="mt-0.5 w-full bg-surface border border-border-default rounded-md px-2 py-1 text-[11px] text-text-primary placeholder:text-text-muted/70 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
                    />
                  </label>
                </div>
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

          {/* 2. Payment Method Selector */}
          <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2">
            <label className="text-xs font-semibold text-text-primary block">Payment Method</label>
            <Select
              value={bill.paymentMethod}
              onChange={(val) => onSetPaymentMethod(val)}
              options={[
                { value: 'CASH', label: 'Cash' },
                { value: 'UPI', label: 'UPI / QR' },
                { value: 'CARD', label: 'Card' },
                { value: 'GIFT_CARD', label: 'Gift Card' },
              ]}
              className="w-full"
            />
            {!requiresTender && (
              <div className="pt-1.5 border-t border-border-default/60 flex items-center justify-between text-xs text-text-muted">
                <span>Exact amount payable</span>
                <span className="font-semibold text-text-primary financial-value">
                  {formatPaise(totals.totalPaise)}
                </span>
              </div>
            )}
          </div>

          {/* 3. Amount Tendered & Dynamic Settlement Status (Cash & Gift Card) */}
          {requiresTender && (
            <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2 animate-fade-in">
              <div className="flex items-center justify-between text-xs gap-2">
                <label
                  htmlFor={`amount-tendered-${bill.index}`}
                  className="font-semibold text-text-primary block"
                >
                  Amount Tendered
                </label>
                <div className="flex bg-surface-secondary rounded-lg p-0.5 border border-border-default shrink-0">
                  <button
                    type="button"
                    onClick={() => onSetFullPayment(true)}
                    className={`px-2.5 py-0.5 rounded text-[11px] font-bold cursor-pointer transition-colors ${
                      bill.isFullPayment !== false
                        ? 'bg-brand text-white shadow-2xs'
                        : 'text-text-secondary hover:text-text-primary'
                    }`}
                  >
                    Full
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      onSetFullPayment(false);
                      // Partial payment must not take in any amount equal to or greater than the bill amount
                      if (totals.totalPaise > 0 && (bill.amountTenderedPaise || 0) >= totals.totalPaise) {
                        onSetAmountTendered(0);
                        setCashTenderedInput('');
                      }
                    }}
                    className={`px-2.5 py-0.5 rounded text-[11px] font-bold cursor-pointer transition-colors ${
                      bill.isFullPayment === false
                        ? 'bg-brand text-white shadow-2xs'
                        : 'text-text-secondary hover:text-text-primary'
                    }`}
                  >
                    Partial
                  </button>
                </div>
              </div>

              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm font-semibold text-text-muted pointer-events-none">
                  {currencySymbol}
                </span>
                <input
                  ref={resolvedTenderedRef}
                  id={`amount-tendered-${bill.index}`}
                  type="text"
                  inputMode="decimal"
                  placeholder="0.00"
                  value={cashTenderedInput}
                  onFocus={() => {
                    cashInputFocusedRef.current = true;
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      if (primaryAction?.onClick) {
                        primaryAction.onClick();
                      }
                    } else if (e.key === 'Escape') {
                      e.preventDefault();
                      emptyProductInputRef.current?.focus();
                    }
                  }}
                  onChange={(e) => {
                    const rawValue = e.target.value;
                    if (rawValue !== '' && !/^\d*\.?\d*$/.test(rawValue)) return;

                    const inputPaise = rupeesToPaise(rawValue);
                    const isFull = bill.isFullPayment !== false;

                    // Partial payment should not take in any amount equal to or greater than the bill amount
                    if (!isFull && totals.totalPaise > 0 && inputPaise >= totals.totalPaise) {
                      toast.warning(
                        `Partial payment cannot be equal to or greater than the bill amount (${formatPaise(totals.totalPaise)}).`,
                        { title: 'Partial Payment Limit' }
                      );
                      return;
                    }

                    setCashTenderedInput(rawValue);
                    onSetAmountTendered(inputPaise);
                  }}
                  onBlur={() => {
                    cashInputFocusedRef.current = false;
                    const paise = bill.amountTenderedPaise || 0;
                    setCashTenderedInput(paise > 0 ? (paise / 100).toFixed(2) : '');
                  }}
                  className="w-full pl-7 pr-3 py-1.5 bg-surface-secondary border border-border-strong rounded-lg text-base financial-value text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand"
                />
              </div>

              {/* Settlement Feedback */}
              {totals.totalPaise > 0 && (
                <div className="pt-1.5 border-t border-border-default/60 space-y-1">
                  {bill.isFullPayment !== false ? (
                    (bill.amountTenderedPaise || 0) >= totals.totalPaise ? (
                      <div className="flex items-center justify-between text-xs font-semibold text-success">
                        <span>Change to return</span>
                        <span className="financial-value">
                          {formatPaise((bill.amountTenderedPaise || 0) - totals.totalPaise)}
                        </span>
                      </div>
                    ) : (
                      <div className="space-y-0.5">
                        <div className="flex items-center justify-between text-xs font-medium text-warning">
                          <span>Balance due</span>
                          <span className="financial-value">
                            {formatPaise(totals.totalPaise - (bill.amountTenderedPaise || 0))}
                          </span>
                        </div>
                        <p className="text-[10px] text-text-muted">
                          Tender at least {formatPaise(totals.totalPaise)} to complete sale.
                        </p>
                      </div>
                    )
                  ) : (
                    <div className="space-y-0.5">
                      <div className="flex items-center justify-between text-xs font-medium text-warning">
                        <span>Remaining balance due</span>
                        <span className="financial-value">
                          {formatPaise(totals.totalPaise - (bill.amountTenderedPaise || 0))}
                        </span>
                      </div>
                      <p className="text-[10px] text-text-muted">
                        Partial payment must be greater than zero and less than{' '}
                        {formatPaise(totals.totalPaise)}.
                      </p>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* 4. Bill-Level Discount */}
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

          {/* 5. Bill Notes */}
          <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2">
            <label className="text-xs font-semibold text-text-primary flex items-center gap-1.5">
              <StickyNote className="w-3.5 h-3.5 text-brand" /> Notes
            </label>
            <textarea
              rows={2}
              value={notes}
              onChange={(e) => onNotesChange?.(e.target.value)}
              placeholder="Internal remarks or delivery notes…"
              className="w-full bg-surface-secondary border border-border-strong rounded-lg px-3 py-2 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand resize-none"
            />
          </div>

          {/* 6. Open Bills Selector */}
          {bills.length > 0 && onSwitchBill && (
            <div className="solid-card p-3 rounded-xl border border-border-default bg-surface space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-text-primary">Bills</span>
                {onClearCart && (
                  <button
                    type="button"
                    onClick={onClearCart}
                    disabled={bill.items.length === 0}
                    className="text-[11px] text-text-secondary hover:text-danger px-2 py-0.5 rounded bg-surface-secondary hover:bg-danger-bg border border-border-default transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    Clear Cart
                  </button>
                )}
              </div>
              <div className="grid grid-cols-9 gap-1">
                {bills.map((b, idx) => {
                  const isActive = idx === activeBillIndex;
                  const hasItems = b.items.length > 0;
                  return (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => onSwitchBill(idx)}
                      aria-current={isActive ? 'true' : undefined}
                      className={`h-8 rounded-lg text-xs font-bold border transition-colors cursor-pointer ${
                        isActive
                          ? 'bg-brand text-white border-brand shadow-xs'
                          : hasItems
                            ? 'bg-warning-bg text-warning border-warning/40 hover:border-warning/70'
                            : 'bg-surface-secondary text-text-secondary border-border-default hover:text-text-primary hover:border-border-strong'
                      }`}
                    >
                      {idx + 1}
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* 8. Complete Sale Persistent Button */}
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
