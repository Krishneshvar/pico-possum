import { formatPaise, rupeesToPaise } from '../../../../lib/formatters';
import { toast } from '../../../../stores/toast.store';
import { strings } from '../../../../lib/strings';

export default function PosCartRow({
  item,
  index,
  getWidthStyle,
  activeCell,
  setActiveCell,
  selectedProductId,
  setSelectedProductId,
  cellRefs,
  skuInputRefs,
  qtyInputRefs,
  unitPriceInputRefs,
  discountInputRefs,
  availableStockFor,
  allowNegativeStock,
  handleCellKeyDown,
  handleQtyKeyDown,
  handleUnitPriceKeyDown,
  handleDiscountKeyDown,
  onUpdateItemQty,
  onUpdateItemPrice,
  onUpdateItemDiscount,
  priceInputs,
  setPriceInputs,
  discountInputs,
  setDiscountInputs,
}) {
  const lineTotal = item.unitPricePaise * item.quantity - (item.discountPaise || 0);
  const availableStock = availableStockFor(item.productId);
  const exceedsStock =
    !allowNegativeStock && availableStock != null && item.quantity > availableStock;
  const isSelected = selectedProductId === item.productId;
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
          if (cellRefs.current) cellRefs.current[`${index}-0`] = el;
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
          if (cellRefs.current) cellRefs.current[`${index}-1`] = el;
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
            if (skuInputRefs.current) skuInputRefs.current[index] = el;
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
          if (cellRefs.current) cellRefs.current[`${index}-2`] = el;
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
                {strings.pos.editor.lineDiscount(formatPaise(item.discountPaise))}
              </span>
            )}
            {exceedsStock && (
              <span className="text-danger font-semibold">
                {strings.pos.editor.onlyInStock(availableStock)}
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
            if (qtyInputRefs.current) qtyInputRefs.current[index] = el;
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
              if (unitPriceInputRefs.current) unitPriceInputRefs.current[index] = el;
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
                  strings.pos.editor.priceExceedsMrp(formatPaise(mrpPaise)),
                  { title: strings.pos.editor.priceExceedsMrpTitle }
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
          if (cellRefs.current) cellRefs.current[`${index}-5`] = el;
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
              if (discountInputRefs.current) discountInputRefs.current[index] = el;
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
          if (cellRefs.current) cellRefs.current[`${index}-7`] = el;
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
}
