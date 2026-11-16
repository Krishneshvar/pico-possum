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
import { formatPaise, rupeesToPaise, getCurrencySymbol } from '../../../lib/formatters';
import Select from '../../../components/common/Select';
import { toast } from '../../../stores/toast.store';
import { strings } from '../../../lib/strings';
import PosCheckoutSidebar from './editor/PosCheckoutSidebar';
import PosCartRow from './editor/PosCartRow';
import PosEmptyCartRow from './editor/PosEmptyCartRow';

const EMPTY_STOCK_ALLOWANCE = new Map();

const POS_TABLE_WIDTHS_KEY = 'pico_possum_pos_table_widths';

const POS_COLUMNS = [
  { key: 'index', label: strings.pos.editor.columns.index, defaultWidth: 48, minWidth: 36, align: 'center' },
  { key: 'sku', label: strings.common.labels.sku, defaultWidth: 120, minWidth: 60, align: 'left' },
  { key: 'product', label: strings.common.labels.product, defaultWidth: 260, minWidth: 140, align: 'left' },
  { key: 'qty', label: strings.pos.editor.columns.qty, defaultWidth: 110, minWidth: 70, align: 'center' },
  { key: 'unitPrice', label: strings.pos.editor.columns.unitPrice, defaultWidth: 120, minWidth: 75, align: 'right' },
  { key: 'mrp', label: strings.pos.editor.columns.mrp, defaultWidth: 110, minWidth: 70, align: 'right' },
  { key: 'discount', label: strings.common.labels.discount, defaultWidth: 110, minWidth: 70, align: 'right' },
  { key: 'lineTotal', label: strings.pos.editor.columns.lineTotal, defaultWidth: 130, minWidth: 80, align: 'right' },
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
        toast.warning(strings.pos.editor.skuNotFound(emptyRowSku.trim()), {
          title: strings.pos.editor.skuNotFoundTitle,
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
            {strings.pos.editor.grandTotal}
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
                          title={strings.common.table.dragToResize}
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
                {bill.items.map((item, index) => (
                  <PosCartRow
                    key={item.productId}
                    item={item}
                    index={index}
                    getWidthStyle={getWidthStyle}
                    activeCell={activeCell}
                    setActiveCell={setActiveCell}
                    selectedProductId={selectedProductId}
                    setSelectedProductId={setSelectedProductId}
                    cellRefs={cellRefs}
                    skuInputRefs={skuInputRefs}
                    qtyInputRefs={qtyInputRefs}
                    unitPriceInputRefs={unitPriceInputRefs}
                    discountInputRefs={discountInputRefs}
                    availableStockFor={availableStockFor}
                    allowNegativeStock={allowNegativeStock}
                    handleCellKeyDown={handleCellKeyDown}
                    handleQtyKeyDown={handleQtyKeyDown}
                    handleUnitPriceKeyDown={handleUnitPriceKeyDown}
                    handleDiscountKeyDown={handleDiscountKeyDown}
                    onUpdateItemQty={onUpdateItemQty}
                    onUpdateItemPrice={onUpdateItemPrice}
                    onUpdateItemDiscount={onUpdateItemDiscount}
                    priceInputs={priceInputs}
                    setPriceInputs={setPriceInputs}
                    discountInputs={discountInputs}
                    setDiscountInputs={setDiscountInputs}
                  />
                ))}

                <PosEmptyCartRow
                  itemsCount={bill.items.length}
                  getWidthStyle={getWidthStyle}
                  activeCell={activeCell}
                  setActiveCell={setActiveCell}
                  cellRefs={cellRefs}
                  emptyRowSkuInputRef={emptyRowSkuInputRef}
                  emptyRowSku={emptyRowSku}
                  setEmptyRowSku={setEmptyRowSku}
                  handleEmptyRowSkuKeyDown={handleEmptyRowSkuKeyDown}
                  emptyProductInputRef={emptyProductInputRef}
                  emptyRowQuery={emptyRowQuery}
                  setEmptyRowQuery={setEmptyRowQuery}
                  emptyRowOpen={emptyRowOpen}
                  setEmptyRowOpen={setEmptyRowOpen}
                  emptyRowResults={emptyRowResults}
                  emptyRowHighlight={emptyRowHighlight}
                  setEmptyRowHighlight={setEmptyRowHighlight}
                  emptyRowResultRefs={emptyRowResultRefs}
                  emptyRowDropdownRef={emptyRowDropdownRef}
                  handleEmptyRowProductKeyDown={handleEmptyRowProductKeyDown}
                  selectEmptyRowProduct={selectEmptyRowProduct}
                  currencySymbol={currencySymbol}
                  handleCellKeyDown={handleCellKeyDown}
                />
              </tbody>
            </table>
          </div>

          {/* Row Edit Drawer if a row is expanded */}
          {expandedRowId && (
            <div className="bg-surface-secondary border-t border-border-default px-4 py-2.5 flex items-center gap-4 text-xs shadow-inner shrink-0">
              <span className="font-semibold text-text-primary">
                {strings.pos.editor.editLine(
                  bill.items.find((it) => it.productId === expandedRowId)?.name || ''
                )}
              </span>
              <div className="flex items-center gap-2">
                <span className="text-text-muted">{strings.pos.editor.unitRate(currencySymbol)}</span>
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
                        strings.pos.editor.priceExceedsMrp(formatPaise(mrpPaise)),
                        { title: strings.pos.editor.priceExceedsMrpTitle }
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
                <span className="text-text-muted">{strings.pos.editor.lineDiscountLabel(currencySymbol)}</span>
                <input
                  type="number"
                  step="0.01"
                  placeholder={strings.pos.editor.amountPlaceholder}
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
        emptyProductInputRef={emptyProductInputRef}
        onSetDiscount={onSetDiscount}
        notes={notes}
        onNotesChange={onNotesChange}
        primaryAction={primaryAction}
      />
    </div>
  );
}
