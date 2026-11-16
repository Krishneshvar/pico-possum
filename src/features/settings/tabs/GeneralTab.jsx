import { RefreshCw, Check } from 'lucide-react';
import Select from '../../../components/common/Select';
import { strings } from '../../../lib/strings';

export default function GeneralTab({ general, setGeneral, onSave, isSaving }) {
  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h2 className="text-base font-semibold text-content">{strings.settings.general.title}</h2>
        <p className="text-xs text-content-secondary">
          {strings.settings.general.subtitle}
        </p>
      </div>

      {/* Store Identity */}
      <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
        <h3 className="text-sm font-semibold text-content">
          {strings.settings.general.storeProfile}
        </h3>
        <div className="space-y-4 text-xs">
          <div>
            <label className="text-content font-medium block mb-1">
              {strings.settings.general.storeName}
            </label>
            <input
              type="text"
              value={general.store_name}
              onChange={(e) => setGeneral({ ...general, store_name: e.target.value })}
              placeholder={strings.settings.general.storeNamePlaceholder}
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="text-content font-medium block mb-1">
                {strings.settings.general.phone}
              </label>
              <input
                type="text"
                value={general.phone}
                onChange={(e) => setGeneral({ ...general, phone: e.target.value })}
                placeholder={strings.settings.general.phonePlaceholder}
                className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-mono"
              />
            </div>
            <div>
              <label className="text-content font-medium block mb-1">
                {strings.settings.general.address}
              </label>
              <input
                type="text"
                value={general.address}
                onChange={(e) => setGeneral({ ...general, address: e.target.value })}
                placeholder={strings.settings.general.addressPlaceholder}
                className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
              />
            </div>
          </div>
        </div>
      </div>

      {/* Date & Time Formats */}
      <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
        <h3 className="text-sm font-semibold text-content">
          {strings.settings.general.dateTimeFormats}
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
          <div>
            <label className="text-content font-medium block mb-1.5">
              {strings.settings.general.dateDisplayFormat}
            </label>
            <Select
              value={general.date_format}
              onChange={(val) => setGeneral({ ...general, date_format: val })}
              options={[
                { value: 'DD-MM-YYYY', label: strings.settings.general.dateFormats.dayFirst },
                { value: 'MM-DD-YYYY', label: strings.settings.general.dateFormats.monthFirst },
                { value: 'YYYY-MM-DD', label: strings.settings.general.dateFormats.iso },
                { value: 'Month Date, Year', label: strings.settings.general.dateFormats.monthNameFirst },
                { value: 'Date Month, Year', label: strings.settings.general.dateFormats.dayBeforeMonthName },
              ]}
            />
          </div>

          <div>
            <label className="text-content font-medium block mb-1.5">
              {strings.settings.general.timeDisplayFormat}
            </label>
            <Select
              value={general.time_format}
              onChange={(val) => setGeneral({ ...general, time_format: val })}
              options={[
                { value: '12 hour format', label: strings.settings.general.timeFormats.twelveHour },
                { value: '24 hour format', label: strings.settings.general.timeFormats.twentyFourHour },
              ]}
            />
          </div>
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
          <span>{strings.settings.general.save}</span>
        </button>
      </div>
    </div>
  );
}
