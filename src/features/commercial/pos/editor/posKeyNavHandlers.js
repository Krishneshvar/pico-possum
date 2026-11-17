import { toast } from '../../../../stores/toast.store';
import { strings } from '../../../../lib/strings';
import { formatPaise, rupeesToPaise } from '../../../../lib/formatters';

export function createPosKeyHandlers({
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
}) {
  const handleCellKeyDown = (e, rowIdx, colIdx, item) => {
    const TOTAL_COLS = 8;
    const maxRow = bill.items.length;

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
      e.preventDefault();
      if (e.shiftKey) {
        if (colIdx > 0) {
          setActiveCell({ row: rowIdx, col: colIdx - 1 });
        } else if (rowIdx > 0) {
          setActiveCell({ row: rowIdx - 1, col: TOTAL_COLS - 1 });
        }
      } else {
        if (colIdx < TOTAL_COLS - 1) {
          setActiveCell({ row: rowIdx, col: colIdx + 1 });
        } else if (rowIdx < maxRow) {
          setActiveCell({ row: rowIdx + 1, col: 0 });
        }
      }
      return;
    }

    if (item && (e.key === 'Delete' || (e.ctrlKey && e.key === 'd'))) {
      e.preventDefault();
      onRemoveItem(item.productId);
      const nextRow = Math.min(rowIdx, bill.items.length - 2);
      if (nextRow >= 0) {
        setActiveCell({ row: nextRow, col: colIdx });
      } else {
        setActiveCell({ row: 0, col: 2 });
      }
      return;
    }

    if (e.key === 'Enter') {
      e.preventDefault();
      if (colIdx === 3) {
        qtyInputRefs.current[rowIdx]?.focus();
        qtyInputRefs.current[rowIdx]?.select();
      } else if (colIdx === 4) {
        unitPriceInputRefs.current[rowIdx]?.focus();
        unitPriceInputRefs.current[rowIdx]?.select();
      } else if (colIdx === 6) {
        discountInputRefs.current[rowIdx]?.focus();
        discountInputRefs.current[rowIdx]?.select();
      } else if (colIdx === 1) {
        skuInputRefs.current[rowIdx]?.focus();
        skuInputRefs.current[rowIdx]?.select();
      } else if (colIdx === 2 && rowIdx === bill.items.length) {
        emptyProductInputRef.current?.focus();
      }
    }
  };

  const handleQtyKeyDown = (e, rowIdx, item) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      setActiveCell({ row: rowIdx, col: 4 });
      unitPriceInputRefs.current[rowIdx]?.focus();
      unitPriceInputRefs.current[rowIdx]?.select();
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
      cellRefs.current[`${rowIdx}-2`]?.focus();
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
        cellRefs.current[`${rowIdx}-2`]?.focus();
      } else {
        setActiveCell({ row: rowIdx, col: 4 });
        unitPriceInputRefs.current[rowIdx]?.focus();
        unitPriceInputRefs.current[rowIdx]?.select();
      }
      return;
    }

    if (e.key === 'Delete') {
      e.preventDefault();
      onRemoveItem(item.productId);
      const nextRow = Math.min(rowIdx, bill.items.length - 2);
      if (nextRow >= 0) {
        setActiveCell({ row: nextRow, col: 3 });
        qtyInputRefs.current[nextRow]?.focus();
        qtyInputRefs.current[nextRow]?.select();
      } else {
        setActiveCell({ row: 0, col: 2 });
        emptyProductInputRef.current?.focus();
      }
    }
  };

  const handleUnitPriceKeyDown = (e, rowIdx, item) => {
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
      const nextRow = Math.min(rowIdx, bill.items.length - 2);
      if (nextRow >= 0) {
        setActiveCell({ row: nextRow, col: 4 });
        unitPriceInputRefs.current[nextRow]?.focus();
        unitPriceInputRefs.current[nextRow]?.select();
      } else {
        setActiveCell({ row: 0, col: 2 });
        emptyProductInputRef.current?.focus();
      }
    }
  };

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
      setActiveCell({ row: rowIdx, col: 7 });
      return;
    }

    if (e.key === 'ArrowLeft') {
      e.preventDefault();
      setActiveCell({ row: rowIdx, col: 5 });
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
        setActiveCell({ row: rowIdx, col: 5 });
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
      const nextRow = Math.min(rowIdx, bill.items.length - 2);
      if (nextRow >= 0) {
        setActiveCell({ row: nextRow, col: 6 });
        discountInputRefs.current[nextRow]?.focus();
        discountInputRefs.current[nextRow]?.select();
      } else {
        setActiveCell({ row: 0, col: 2 });
        emptyProductInputRef.current?.focus();
      }
    }
  };

  const handleEmptyRowSkuKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      const val = (emptyRowSku || '').trim();
      if (!val) return;
      const found = findProductBySku(val);
      if (found) {
        handleSelectProduct(found);
      } else {
        toast.warning(strings.pos.toasts.noProductFoundForSku(val));
      }
      return;
    }

    if (e.key === 'ArrowRight' || e.key === 'Tab') {
      e.preventDefault();
      setActiveCell({ row: bill.items.length, col: 2 });
      emptyProductInputRef.current?.focus();
      return;
    }

    if (e.key === 'ArrowLeft') {
      e.preventDefault();
      setActiveCell({ row: bill.items.length, col: 0 });
      return;
    }

    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (bill.items.length > 0) {
        setActiveCell({ row: bill.items.length - 1, col: 1 });
      }
    }
  };

  const handleEmptyRowProductKeyDown = (e, emptyRowResults, selectEmptyRowProduct) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (emptyRowResults.length > 0) {
        setHighlightedIndex((prev) => (prev < emptyRowResults.length - 1 ? prev + 1 : 0));
      }
      return;
    }

    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (emptyRowResults.length > 0) {
        setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : emptyRowResults.length - 1));
      } else if (bill.items.length > 0) {
        setActiveCell({ row: bill.items.length - 1, col: 2 });
      }
      return;
    }

    if (e.key === 'Enter') {
      e.preventDefault();
      const val = e.currentTarget.value.trim();
      if (!val) {
        return;
      }
      const found = findProductBySku(val);
      if (found) {
        handleSelectProduct(found);
      } else if (emptyRowResults.length > 0) {
        selectEmptyRowProduct(emptyRowResults[0]);
      } else {
        toast.warning(strings.pos.toasts.noProductMatching(val));
      }
      return;
    }

    if (e.key === 'ArrowLeft') {
      if (e.currentTarget.selectionStart === 0 && e.currentTarget.selectionEnd === 0) {
        e.preventDefault();
        setActiveCell({ row: bill.items.length, col: 1 });
        emptyRowSkuInputRef.current?.focus();
      }
      return;
    }

    if (e.key === 'ArrowRight') {
      if (e.currentTarget.selectionStart === e.currentTarget.value.length) {
        e.preventDefault();
        setActiveCell({ row: bill.items.length, col: 3 });
      }
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
    }
  };

  return {
    handleCellKeyDown,
    handleQtyKeyDown,
    handleUnitPriceKeyDown,
    handleDiscountKeyDown,
    handleEmptyRowSkuKeyDown,
    handleEmptyRowProductKeyDown,
  };
}
