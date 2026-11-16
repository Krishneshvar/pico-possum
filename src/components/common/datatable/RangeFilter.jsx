import { strings } from '../../../lib/strings';

export default function RangeFilter({ label, unit, minVal, maxVal, onMinChange, onMaxChange }) {
  return (
    <div className="flex items-center gap-1.5 text-xs text-text-secondary">
      <span className="whitespace-nowrap">{label}:</span>
      <input
        type="number"
        step="any"
        placeholder={strings.common.table.minPlaceholder(unit)}
        value={minVal}
        onChange={(e) => onMinChange(e.target.value)}
        onInput={(e) => onMinChange(e.target.value)}
        className="w-20 bg-surface border border-border-default text-text-primary rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-brand font-mono transition-colors"
      />
      <span className="text-text-muted">–</span>
      <input
        type="number"
        step="any"
        placeholder={strings.common.table.maxPlaceholder(unit)}
        value={maxVal}
        onChange={(e) => onMaxChange(e.target.value)}
        onInput={(e) => onMaxChange(e.target.value)}
        className="w-20 bg-surface border border-border-default text-text-primary rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-brand font-mono transition-colors"
      />
    </div>
  );
}
