import { Search } from 'lucide-react';
import { formatPaise } from '../../../../lib/formatters';

export default function PosEmptyCartRow({
  itemsCount,
  getWidthStyle,
  activeCell,
  setActiveCell,
  cellRefs,
  emptyRowSkuInputRef,
  emptyRowSku,
  setEmptyRowSku,
  handleEmptyRowSkuKeyDown,
  emptyProductInputRef,
  emptyRowQuery,
  setEmptyRowQuery,
  emptyRowOpen,
  setEmptyRowOpen,
  emptyRowResults,
  emptyRowHighlight,
  setEmptyRowHighlight,
  emptyRowResultRefs,
  emptyRowDropdownRef,
  handleEmptyRowProductKeyDown,
  selectEmptyRowProduct,
  currencySymbol,
  handleCellKeyDown,
}) {
  return (
    <tr
      className={`transition-colors border-b border-border-default ${
        activeCell?.row === itemsCount
          ? 'bg-brand-subtle/10'
          : itemsCount % 2 === 1
          ? 'bg-surface-secondary/25'
          : 'bg-surface'
      }`}
    >
      {/* 1. # */}
      <td
        tabIndex={0}
        ref={(el) => {
          if (cellRefs.current) cellRefs.current[`${itemsCount}-0`] = el;
        }}
        style={getWidthStyle('index')}
        onClick={() => {
          setActiveCell({ row: itemsCount, col: 0 });
        }}
        onFocus={() => {
          setActiveCell({ row: itemsCount, col: 0 });
        }}
        onKeyDown={(e) => handleCellKeyDown(e, itemsCount, 0, null)}
        className={`py-2 px-3 text-center text-text-muted font-mono text-[11px] border-r border-border-default relative outline-none transition-colors ${
          activeCell?.row === itemsCount && activeCell?.col === 0
            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
            : ''
        }`}
      >
        {itemsCount + 1}
      </td>

      {/* 2. SKU (Input) */}
      <td
        tabIndex={-1}
        style={getWidthStyle('sku')}
        onClick={() => {
          setActiveCell({ row: itemsCount, col: 1 });
          emptyRowSkuInputRef.current?.focus();
          emptyRowSkuInputRef.current?.select();
        }}
        className={`p-1.5 font-mono text-[11px] border-r border-border-default whitespace-nowrap relative outline-none transition-colors ${
          activeCell?.row === itemsCount && activeCell?.col === 1
            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
            : ''
        }`}
      >
        <input
          ref={(el) => {
            if (emptyRowSkuInputRef) emptyRowSkuInputRef.current = el;
            if (cellRefs.current) cellRefs.current[`${itemsCount}-1`] = el;
          }}
          type="text"
          placeholder=""
          value={emptyRowSku}
          onChange={(e) => setEmptyRowSku(e.target.value)}
          onFocus={() => {
            setActiveCell({ row: itemsCount, col: 1 });
          }}
          onKeyDown={handleEmptyRowSkuKeyDown}
          className="w-full px-2 py-1.5 rounded-lg bg-surface-secondary/70 border border-border-strong text-xs font-mono text-text-primary placeholder:text-text-muted/70 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
        />
      </td>

      {/* 3. Product (Search & Input cell with Autocomplete) */}
      <td
        style={getWidthStyle('product')}
        className={`p-1.5 border-r border-border-default relative ${
          activeCell?.row === itemsCount && activeCell?.col === 2
            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
            : ''
        }`}
      >
        <div className="relative">
          <div className="relative flex items-center">
            <Search className="w-3.5 h-3.5 text-text-muted absolute left-2.5 pointer-events-none" />
            <input
              ref={(el) => {
                if (emptyProductInputRef) emptyProductInputRef.current = el;
                if (cellRefs.current) cellRefs.current[`${itemsCount}-2`] = el;
              }}
              type="text"
              placeholder="Search product (or scan barcode)..."
              value={emptyRowQuery}
              onChange={(e) => {
                setEmptyRowQuery(e.target.value);
                setEmptyRowOpen(true);
              }}
              onFocus={() => {
                setActiveCell({ row: itemsCount, col: 2 });
                if (emptyRowQuery.trim().length > 0) setEmptyRowOpen(true);
              }}
              onKeyDown={handleEmptyRowProductKeyDown}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg bg-surface-secondary/70 border border-border-strong text-xs text-text-primary placeholder:text-text-muted/70 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
            />
          </div>

          {/* Autocomplete Dropdown popup */}
          {emptyRowOpen && emptyRowResults.length > 0 && (
            <div
              ref={emptyRowDropdownRef}
              className="absolute left-0 top-full mt-1 w-[380px] max-h-64 overflow-y-auto bg-surface border border-border-strong rounded-xl shadow-2xl z-50 py-1"
            >
              {emptyRowResults.map((p, pIdx) => {
                const isHl = pIdx === emptyRowHighlight;
                return (
                  <div
                    key={p.id}
                    ref={(el) => {
                      if (emptyRowResultRefs.current) emptyRowResultRefs.current[pIdx] = el;
                    }}
                    onMouseEnter={() => setEmptyRowHighlight(pIdx)}
                    onClick={() => selectEmptyRowProduct(p)}
                    className={`px-3 py-2 flex items-center justify-between text-xs cursor-pointer border-b border-border-default/40 last:border-b-0 transition-colors ${
                      isHl ? 'bg-brand text-white' : 'hover:bg-surface-secondary text-text-primary'
                    }`}
                  >
                    <div className="min-w-0 pr-2">
                      <div className={`font-semibold truncate ${isHl ? 'text-white' : 'text-text-primary'}`}>
                        {p.name}
                      </div>
                      <div className={`text-[10px] font-mono ${isHl ? 'text-white/80' : 'text-text-muted'}`}>
                        {p.sku || 'No SKU'}
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <div className={`font-mono font-bold ${isHl ? 'text-white' : 'text-text-primary'}`}>
                        {formatPaise(p.mrp_paise)}
                      </div>
                      <div
                        className={`text-[10px] ${
                          isHl
                            ? 'text-white/80'
                            : p.current_stock <= (p.min_stock_alert ?? 5)
                            ? 'text-danger font-semibold'
                            : 'text-text-muted'
                        }`}
                      >
                        Stock: {p.current_stock}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </td>

      {/* 4. Qty */}
      <td
        tabIndex={0}
        ref={(el) => {
          if (cellRefs.current) cellRefs.current[`${itemsCount}-3`] = el;
        }}
        style={getWidthStyle('qty')}
        onClick={() => {
          setActiveCell({ row: itemsCount, col: 3 });
        }}
        onFocus={() => {
          setActiveCell({ row: itemsCount, col: 3 });
        }}
        onKeyDown={(e) => handleCellKeyDown(e, itemsCount, 3, null)}
        className={`py-2 px-3 text-center text-text-muted font-mono text-xs border-r border-border-default relative outline-none transition-colors ${
          activeCell?.row === itemsCount && activeCell?.col === 3
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
          if (cellRefs.current) cellRefs.current[`${itemsCount}-4`] = el;
        }}
        style={getWidthStyle('unitPrice')}
        onClick={() => {
          setActiveCell({ row: itemsCount, col: 4 });
        }}
        onFocus={() => {
          setActiveCell({ row: itemsCount, col: 4 });
        }}
        onKeyDown={(e) => handleCellKeyDown(e, itemsCount, 4, null)}
        className={`py-2 px-3 text-right text-text-muted font-mono text-xs border-r border-border-default relative outline-none transition-colors ${
          activeCell?.row === itemsCount && activeCell?.col === 4
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
          if (cellRefs.current) cellRefs.current[`${itemsCount}-5`] = el;
        }}
        style={getWidthStyle('mrp')}
        onClick={() => {
          setActiveCell({ row: itemsCount, col: 5 });
        }}
        onFocus={() => {
          setActiveCell({ row: itemsCount, col: 5 });
        }}
        onKeyDown={(e) => handleCellKeyDown(e, itemsCount, 5, null)}
        className={`py-2 px-3 text-right text-text-muted font-mono text-xs border-r border-border-default relative outline-none transition-colors ${
          activeCell?.row === itemsCount && activeCell?.col === 5
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
          if (cellRefs.current) cellRefs.current[`${itemsCount}-6`] = el;
        }}
        style={getWidthStyle('discount')}
        onClick={() => {
          setActiveCell({ row: itemsCount, col: 6 });
        }}
        onFocus={() => {
          setActiveCell({ row: itemsCount, col: 6 });
        }}
        onKeyDown={(e) => handleCellKeyDown(e, itemsCount, 6, null)}
        className={`py-2 px-3 text-right text-text-muted font-mono text-xs border-r border-border-default relative outline-none transition-colors ${
          activeCell?.row === itemsCount && activeCell?.col === 6
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
          if (cellRefs.current) cellRefs.current[`${itemsCount}-7`] = el;
        }}
        style={getWidthStyle('lineTotal')}
        onClick={() => {
          setActiveCell({ row: itemsCount, col: 7 });
        }}
        onFocus={() => {
          setActiveCell({ row: itemsCount, col: 7 });
        }}
        onKeyDown={(e) => handleCellKeyDown(e, itemsCount, 7, null)}
        className={`py-2 px-3 text-right text-text-muted font-mono text-xs border-r-0 relative outline-none transition-colors ${
          activeCell?.row === itemsCount && activeCell?.col === 7
            ? 'ring-2 ring-brand ring-inset bg-brand-subtle/30 z-10'
            : ''
        }`}
      >
        —
      </td>
    </tr>
  );
}
