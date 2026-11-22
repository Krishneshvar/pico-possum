import { RefreshCw, Check } from 'lucide-react';
import { strings } from '../../../lib/strings';

export default function StoreTaxTab({ general, setGeneral, onSave, isSaving }) {
  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h2 className="text-base font-semibold text-content">{strings.settings.storeTax.title}</h2>
        <p className="text-xs text-content-secondary">
          {strings.settings.storeTax.subtitle}
        </p>
      </div>

      {/* Currency & Tax Registration */}
      <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
        <h3 className="text-sm font-semibold text-content">
          {strings.settings.storeTax.currencyGstin}
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
          <div>
            <label className="text-content font-medium block mb-1">
              {strings.settings.storeTax.currencySymbol}
            </label>
            <input
              type="text"
              value={general.currencySymbol ?? general.currency_symbol ?? ''}
              onChange={(e) => setGeneral({ ...general, currencySymbol: e.target.value })}
              placeholder={strings.settings.storeTax.currencySymbolPlaceholder}
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-medium"
            />
            <span className="text-[11px] text-content-muted mt-1 block">
              {strings.settings.storeTax.currencySymbolHint}
            </span>
          </div>

          <div>
            <label className="text-content font-medium block mb-1">
              {strings.settings.storeTax.currencyFormat}
            </label>
            <select
              value={general.currencyFormat ?? general.currency_format ?? 'en-IN'}
              onChange={(e) => setGeneral({ ...general, currencyFormat: e.target.value })}
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-medium cursor-pointer"
            >
              <option value="en-IN">{strings.settings.storeTax.currencyFormats.indian}</option>
              <option value="en-US">{strings.settings.storeTax.currencyFormats.international}</option>
              <option value="de-DE">{strings.settings.storeTax.currencyFormats.european}</option>
              <option value="fr-FR">{strings.settings.storeTax.currencyFormats.space}</option>
            </select>
            <span className="text-[11px] text-content-muted mt-1 block">
              {strings.settings.storeTax.currencyFormatHint}
            </span>
          </div>

          <div>
            <label className="text-content font-medium block mb-1">
              {strings.settings.storeTax.gstin}
            </label>
            <input
              type="text"
              value={general.gstin ?? ''}
              onChange={(e) => setGeneral({ ...general, gstin: e.target.value })}
              placeholder={strings.settings.storeTax.gstinPlaceholder}
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-mono"
            />
            <span className="text-[11px] text-content-muted mt-1 block">
              {strings.settings.storeTax.gstinHint}
            </span>
          </div>
        </div>
      </div>

      {/* Inventory Guard Rails */}
      <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
        <h3 className="text-sm font-semibold text-content">
          {strings.settings.storeTax.inventoryBehavior}
        </h3>
        <div className="space-y-3 text-xs">
          <label
            className={`flex items-start gap-3 p-3.5 rounded-lg border cursor-pointer transition-colors ${
              (general.autoNumericSku ?? general.auto_numeric_sku)
                ? 'bg-brand-subtle border-brand/40'
                : 'bg-surface-secondary border-border hover:border-border-strong'
            }`}
          >
            <input
              type="checkbox"
              checked={general.autoNumericSku ?? general.auto_numeric_sku ?? true}
              onChange={(e) => setGeneral({ ...general, autoNumericSku: e.target.checked })}
              className="w-4 h-4 mt-0.5 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
            />
            <div className="space-y-1">
              <span className="font-semibold text-content block">
                {strings.settings.storeTax.autoSkuTitle}
              </span>
              <span className="text-content-secondary block">
                {strings.settings.storeTax.autoSkuDescription}
              </span>
            </div>
          </label>

          <label
            className={`flex items-start gap-3 p-3.5 rounded-lg border cursor-pointer transition-colors ${
              (general.allowNegativeStock ?? general.allow_negative_stock)
                ? 'bg-warning/10 border-warning/40'
                : 'bg-surface-secondary border-border hover:border-border-strong'
            }`}
          >
            <input
              type="checkbox"
              checked={general.allowNegativeStock ?? general.allow_negative_stock ?? false}
              onChange={(e) => setGeneral({ ...general, allowNegativeStock: e.target.checked })}
              className="w-4 h-4 mt-0.5 rounded border-border-strong text-warning focus:ring-warning cursor-pointer"
            />
            <div className="space-y-1">
              <span className="font-semibold text-content block">
                {strings.settings.storeTax.negativeStockTitle}
              </span>
              <span className="text-content-secondary block">
                {strings.settings.storeTax.negativeStockDescription}
              </span>
            </div>
          </label>
        </div>
      </div>

      {/* Save CTA */}
      <div className="flex items-center gap-3 pt-2">
        <button
          type="button"
          onClick={() => onSave(general)}
          disabled={isSaving}
          className="px-5 py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer"
        >
          {isSaving ? (
            <RefreshCw className="w-4 h-4 animate-spin" />
          ) : (
            <Check className="w-4 h-4" />
          )}
          <span>{strings.settings.storeTax.save}</span>
        </button>
      </div>
    </div>
  );
}
