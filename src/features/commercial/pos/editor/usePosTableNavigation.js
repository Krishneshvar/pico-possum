import { useState, useRef, useEffect } from 'react';
import { usePosTableColumns, POS_COLUMNS, POS_TABLE_WIDTHS_KEY } from './usePosTableColumns';
import { createPosKeyHandlers } from './posKeyNavHandlers';

export { POS_COLUMNS, POS_TABLE_WIDTHS_KEY };

export function usePosTableNavigation({
  bill,
  products = [],
  stockProducts = [],
  onAddItem,
  onUpdateItemQty,
  onUpdateItemPrice,
  onUpdateItemDiscount,
  onRemoveItem,
  onSearchQueryChange,
  onSearchFocusedChange,
  setSelectedProductId,
  setExpandedRowId,
}) {
  const [activeCell, setActiveCell] = useState({ row: 0, col: 2 });
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const [emptyRowSku, setEmptyRowSku] = useState('');
  const [priceInputs, setPriceInputs] = useState({});
  const [discountInputs, setDiscountInputs] = useState({});

  const { colWidths, setColWidths, getWidthStyle, startResizing } = usePosTableColumns();

  const cellRefs = useRef({});
  const qtyInputRefs = useRef({});
  const unitPriceInputRefs = useRef({});
  const discountInputRefs = useRef({});
  const skuInputRefs = useRef({});
  const emptyRowSkuInputRef = useRef(null);
  const emptyProductInputRef = useRef(null);

  // Default focus on empty row's product cell on mount or bill switch
  useEffect(() => {
    setActiveCell({ row: bill.items.length, col: 2 });
    const timer = setTimeout(() => {
      emptyProductInputRef.current?.focus();
    }, 40);
    return () => clearTimeout(timer);
  }, [bill.index]);

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

    setTimeout(() => {
      setActiveCell({ row: targetIdx, col: 3 });
      const qtyInput = qtyInputRefs.current[targetIdx];
      if (qtyInput) {
        qtyInput.focus();
        qtyInput.select();
      }
    }, 40);
  };

  const handlers = createPosKeyHandlers({
    bill,
    setActiveCell,
    cellRefs,
    qtyInputRefs,
    unitPriceInputRefs,
    discountInputRefs,
    skuInputRefs,
    emptyRowSkuInputRef,
    emptyProductInputRef,
    setHighlightedIndex,
    priceInputs,
    setPriceInputs,
    discountInputs,
    setDiscountInputs,
    emptyRowSku,
    setEmptyRowSku,
    onUpdateItemQty,
    onUpdateItemPrice,
    onUpdateItemDiscount,
    onRemoveItem,
    onAddItem,
    onSearchQueryChange,
    onSearchFocusedChange,
    setSelectedProductId,
    setExpandedRowId,
    findProductBySku,
    handleSelectProduct,
  });

  return {
    activeCell,
    setActiveCell,
    highlightedIndex,
    setHighlightedIndex,
    emptyRowSku,
    setEmptyRowSku,
    priceInputs,
    setPriceInputs,
    discountInputs,
    setDiscountInputs,
    colWidths,
    setColWidths,
    getWidthStyle,
    startResizing,
    cellRefs,
    qtyInputRefs,
    unitPriceInputRefs,
    discountInputRefs,
    skuInputRefs,
    emptyRowSkuInputRef,
    emptyProductInputRef,
    findProductBySku,
    handleSelectProduct,
    ...handlers,
  };
}
