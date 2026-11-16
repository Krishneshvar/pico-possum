import DatePicker from '../DatePicker';
import { strings } from '../../../lib/strings';

export default function DateRangeFilter({ label, fromVal, toVal, onFromChange, onToChange }) {
  return (
    <div className="flex items-center gap-1.5 text-xs text-text-secondary">
      <span className="whitespace-nowrap font-medium text-text-primary">{label}:</span>
      <DatePicker
        value={fromVal}
        onChange={onFromChange}
        placeholder={strings.common.placeholders.startDate}
      />
      <span className="text-text-muted">–</span>
      <DatePicker
        value={toVal}
        onChange={onToChange}
        placeholder={strings.common.placeholders.endDate}
      />
    </div>
  );
}
