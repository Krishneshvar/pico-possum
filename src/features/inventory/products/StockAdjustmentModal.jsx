import { useState } from 'react';
import { X, SlidersHorizontal, ShoppingBag } from 'lucide-react';
import { strings } from '../../../lib/strings';
import { toast } from '../../../stores/toast.store';
import { validateStockAdjustment } from '../../../lib/validation';

export default function StockAdjustmentModal({
  product,
  onClose,
  onSubmit,
  isPending = false,
  allowNegativeStock = false,
}) {
  const [stockAdjustment, setStockAdjustment] = useState({
    quantityChange: '',
    movementType: 'MANUAL_STOCK_CORRECTION',
    note: '',
  });
  const [errors, setErrors] = useState({});

  if (!product) return null;

  const currentStock = product.currentStock ?? product.current_stock ?? 0;
  const parsedQty = parseInt(stockAdjustment.quantityChange, 10);
  const isValidQty = !isNaN(parsedQty) && parsedQty !== 0;
  const isNegative = isValidQty && parsedQty < 0;
  const projectedStock = isValidQty ? currentStock + parsedQty : null;

  const handleSelectReason = (reason) => {
    if (reason === 'PURCHASE' && isNegative) {
      toast.warning(strings.products.stock.purchaseNegativeWarning);
      return;
    }
    setErrors((prev) => ({ ...prev, reason: undefined }));
    setStockAdjustment((prev) => ({ ...prev, movementType: reason }));
  };

  const updateQtyVal = (rawVal) => {
    setErrors((prev) => ({ ...prev, quantityChange: undefined }));
    const nextParsed = parseInt(rawVal, 10);
    const willBeNegative = !isNaN(nextParsed) && nextParsed < 0;
    setStockAdjustment((prev) => ({
      ...prev,
      quantityChange: rawVal,
      movementType: willBeNegative && prev.movementType === 'PURCHASE'
        ? 'MANUAL_STOCK_CORRECTION'
        : prev.movementType,
    }));
  };

  const handleConfirm = () => {
    const validation = validateStockAdjustment(
      stockAdjustment.quantityChange,
      stockAdjustment.note,
      stockAdjustment.movementType,
      currentStock,
      allowNegativeStock
    );
    if (!validation.isValid) {
      setErrors(validation.errors);
      const firstError = Object.values(validation.errors)[0];
      if (firstError) toast.warning(firstError);
      return;
    }
    onSubmit({
      productId: product.id,
      quantityChange: parsedQty,
      reason: stockAdjustment.movementType || 'MANUAL_STOCK_CORRECTION',
      note: stockAdjustment.note ? stockAdjustment.note.trim() : null,
    });
  };

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
      <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-xl text-text-primary animate-scale-in">
        <div className="flex justify-between items-center border-b border-border-default pb-3">
          <div>
            <h3 className="font-bold text-sm text-text-primary">{strings.products.actions.adjustStock}</h3>
            <p className="text-[11px] text-text-muted truncate max-w-[200px]">{product.name}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="bg-surface-secondary p-3 rounded-lg border border-border-default text-xs flex items-center justify-between">
          <span className="text-text-muted">{strings.products.stock.currentBalance}</span>
          <div className="flex items-center gap-2">
            <strong className="text-text-primary font-mono font-bold text-sm">
              {strings.products.stock.units(currentStock)}
            </strong>
            {isValidQty && (
              <span className="text-[11px] font-mono font-semibold text-brand">
                {strings.products.stock.projectedUnits(projectedStock)}
              </span>
            )}
          </div>
        </div>

        <div className="space-y-3 text-xs">
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-text-secondary font-semibold">
                {strings.products.stock.reason} <span className="text-danger">*</span>
              </label>
              {isNegative && (
                <span className="text-[10px] text-text-muted font-mono">
                  {strings.products.stock.negativeHint}
                </span>
              )}
            </div>

            <div className="grid grid-cols-2 gap-2 p-1 bg-surface-secondary border border-border-default rounded-xl">
              <button
                type="button"
                onClick={() => handleSelectReason('MANUAL_STOCK_CORRECTION')}
                className={`py-2 px-3 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-all cursor-pointer ${
                  stockAdjustment.movementType === 'MANUAL_STOCK_CORRECTION'
                    ? 'bg-brand text-white shadow-xs'
                    : 'text-text-muted hover:text-text-primary'
                }`}
              >
                <SlidersHorizontal className="w-3.5 h-3.5" />
                <span>{strings.products.stock.manualCorrection}</span>
              </button>

              <button
                type="button"
                disabled={isNegative}
                onClick={() => handleSelectReason('PURCHASE')}
                title={
                  isNegative
                    ? strings.products.stock.purchaseDisabledTooltip
                    : strings.products.stock.purchaseTooltip
                }
                className={`py-2 px-3 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${
                  isNegative
                    ? 'opacity-40 cursor-not-allowed text-text-muted bg-surface/30'
                    : stockAdjustment.movementType === 'PURCHASE'
                    ? 'bg-brand text-white shadow-xs'
                    : 'text-text-muted hover:text-text-primary cursor-pointer'
                }`}
              >
                <ShoppingBag className="w-3.5 h-3.5" />
                <span>{strings.products.stock.purchase}</span>
              </button>
            </div>
            {errors.reason && <p className="text-[11px] text-danger mt-1">{errors.reason}</p>}
          </div>

          <div>
            <label className="text-text-secondary block font-semibold mb-1">
              {strings.products.stock.quantityChange}
            </label>
            <div className="relative">
              <input
                type="text"
                inputMode="numeric"
                value={stockAdjustment.quantityChange}
                onChange={(e) => {
                  const val = e.target.value.trim();
                  if (val === '' || val === '-' || val === '+' || /^[+-]?\d+$/.test(val)) {
                    updateQtyVal(val);
                  }
                }}
                placeholder={strings.products.stock.placeholders.quantity}
                className={`w-full bg-surface-secondary border rounded-lg p-2 pr-16 text-text-primary focus:outline-none font-mono ${
                  errors.quantityChange ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
                }`}
              />
              <div className="absolute right-1.5 top-1/2 -translate-y-1/2 flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => {
                    const current = parseInt(stockAdjustment.quantityChange, 10) || 0;
                    const next = current + 1;
                    updateQtyVal(next > 0 ? `+${next}` : String(next));
                  }}
                  className="px-2 py-0.5 text-xs font-bold bg-surface rounded border border-border-default hover:bg-surface-secondary text-text-primary cursor-pointer transition-colors"
                  title={strings.products.stock.addOne}
                >
                  +
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const current = parseInt(stockAdjustment.quantityChange, 10) || 0;
                    const next = current - 1;
                    updateQtyVal(next > 0 ? `+${next}` : String(next));
                  }}
                  className="px-2 py-0.5 text-xs font-bold bg-surface rounded border border-border-default hover:bg-surface-secondary text-text-primary cursor-pointer transition-colors"
                  title={strings.products.stock.deductOne}
                >
                  −
                </button>
              </div>
            </div>
            {errors.quantityChange && <p className="text-[11px] text-danger mt-1">{errors.quantityChange}</p>}
          </div>

          <div>
            <label className="text-text-secondary block font-semibold mb-1">
              {strings.products.stock.noteLabel}
            </label>
            <input
              type="text"
              value={stockAdjustment.note}
              onChange={(e) => {
                setErrors((prev) => ({ ...prev, note: undefined }));
                setStockAdjustment({ ...stockAdjustment, note: e.target.value });
              }}
              placeholder={strings.products.stock.placeholders.note}
              className={`w-full bg-surface-secondary border rounded-lg p-2 text-text-primary focus:outline-none ${
                errors.note ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
              }`}
            />
            {errors.note && <p className="text-[11px] text-danger mt-1">{errors.note}</p>}
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2 border-t border-border-default">
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1.5 rounded-lg text-xs font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={isPending || !isValidQty}
            className="px-4 py-1.5 rounded-lg text-xs font-bold bg-brand text-white hover:bg-brand-hover shadow-xs disabled:opacity-50 cursor-pointer"
          >
            Confirm
          </button>
        </div>
      </div>
    </div>
  );
}
