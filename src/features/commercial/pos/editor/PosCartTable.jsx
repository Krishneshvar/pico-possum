import { formatPaise, rupeesToPaise } from '../../../../lib/formatters';
import { strings } from '../../../../lib/strings';
import { toast } from '../../../../stores/toast.store';
import PosCartRow from './PosCartRow';
import PosEmptyCartRow from './PosEmptyCartRow';
import { POS_COLUMNS } from './usePosTableColumns';

export default function PosCartTable({
  bill,
  totals,
  getWidthStyle,
  startResizing,
  setColWidths,
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
  expandedRowId,
  setExpandedRowId,
}) {
  return (
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

      {/* Cart Items Table */}
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
                className="w-24 px-2 py-1 rounded border border-border-strong bg-surface text-text-primary font-mono text-xs focus:border-brand focus:outline-none"
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
  );
}
