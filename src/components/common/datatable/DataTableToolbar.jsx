import { X } from 'lucide-react';
import Select from '../Select';
import MultiSelectDropdown from './MultiSelectDropdown';
import RangeFilter from './RangeFilter';
import DateRangeFilter from './DateRangeFilter';
import { strings } from '../../../lib/strings';

export default function DataTableToolbar({
  search,
  handleSearch,
  enrichedFilters,
  selectValues,
  handleSelect,
  multiValues,
  handleMulti,
  rangeValues,
  handleRangeMin,
  handleRangeMax,
  dateRangeKey,
  dateValues,
  handleDateFrom,
  handleDateTo,
  hasActiveFilters,
  clearAllFilters,
  headerRight,
  filtered,
}) {
  return (
    <div className="h-12 px-4 border-b border-border-default flex items-center gap-2.5 shrink-0 overflow-x-auto">
      {/* Search Input */}
      <div className="relative w-56 shrink-0">
        <svg
          className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-text-muted pointer-events-none"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z"
          />
        </svg>
        <input
          type="text"
          value={search}
          onChange={(e) => handleSearch(e.target.value)}
          placeholder={strings.common.table.searchRecords}
          className="w-full pl-8 pr-3 py-1.5 bg-surface-secondary border border-border-default rounded-lg text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
        />
      </div>

      {/* Dynamic Filters */}
      {enrichedFilters.map((f) => {
        const type = f.type ?? 'select';

        if (type === 'select') {
          return (
            <Select
              key={f.key}
              value={selectValues[f.key] ?? ''}
              onChange={(val) => handleSelect(f.key, val)}
              placeholder={f.label}
              options={[
                { value: '', label: strings.common.table.allFilter(f.label) },
                ...f.options,
              ]}
            />
          );
        }

        if (type === 'multiselect') {
          return (
            <MultiSelectDropdown
              key={f.key}
              label={f.label}
              options={f.options}
              selected={multiValues[f.key] ?? []}
              onChange={(vals) => handleMulti(f.key, vals)}
            />
          );
        }

        if (type === 'range') {
          const rv = rangeValues[f.key] ?? {};
          return (
            <RangeFilter
              key={f.key}
              label={f.label}
              unit={f.unit}
              minVal={rv.min ?? ''}
              maxVal={rv.max ?? ''}
              onMinChange={(v) => handleRangeMin(f.key, v)}
              onMaxChange={(v) => handleRangeMax(f.key, v)}
            />
          );
        }

        if (type === 'daterange') {
          const dk = dateRangeKey(f);
          const dv = dateValues[dk] ?? {};
          const curFrom = f.fromVal !== undefined ? f.fromVal : (dv.from ?? '');
          const curTo = f.toVal !== undefined ? f.toVal : (dv.to ?? '');
          return (
            <DateRangeFilter
              key={dk}
              label={f.label}
              fromVal={curFrom}
              toVal={curTo}
              onFromChange={(v) => {
                handleDateFrom(dk, v);
                f.onFromChange?.(v);
              }}
              onToChange={(v) => {
                handleDateTo(dk, v);
                f.onToChange?.(v);
              }}
            />
          );
        }

        return null;
      })}

      {hasActiveFilters && (
        <button
          type="button"
          onClick={clearAllFilters}
          className="flex items-center gap-1 text-[11px] font-medium text-danger hover:underline transition-colors cursor-pointer px-1 py-0.5"
        >
          <X className="w-3 h-3" /> {strings.common.table.clearFilters}
        </button>
      )}

      <div className="flex-1" />
      {typeof headerRight === 'function' ? headerRight({ filtered }) : headerRight}
    </div>
  );
}
