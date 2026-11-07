import { useState, useRef, useEffect, useMemo } from 'react';
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight, X } from 'lucide-react';

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

const SHORT_DAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

/**
 * Format Date to YYYY-MM-DD in local time
 */
export function formatToIso(date) {
  if (!date || isNaN(date.getTime())) return '';
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * Parse YYYY-MM-DD into a local Date object
 */
export function parseIsoDate(isoStr) {
  if (!isoStr || typeof isoStr !== 'string') return null;
  const parts = isoStr.split('-');
  if (parts.length !== 3) return null;
  const [y, m, d] = parts.map((n) => parseInt(n, 10));
  if (isNaN(y) || isNaN(m) || isNaN(d)) return null;
  return new Date(y, m - 1, d);
}

/**
 * Format for friendly display: e.g. "07 Nov 2026" or YYYY-MM-DD
 */
export function formatDisplayDate(isoStr) {
  const d = parseIsoDate(isoStr);
  if (!d) return '';
  const day = String(d.getDate()).padStart(2, '0');
  const month = MONTH_NAMES[d.getMonth()].slice(0, 3);
  const year = d.getFullYear();
  return `${day} ${month} ${year}`;
}

/**
 * Proper React DatePicker Component
 * Replaces native <input type="date"> with a sleek, accessible, modern UI popover calendar.
 */
export default function DatePicker({
  value = '',
  onChange,
  placeholder = 'Select date',
  minDate = null,
  maxDate = null,
  disabled = false,
  className = '',
  buttonClassName = '',
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  const selectedDate = useMemo(() => parseIsoDate(value), [value]);

  // Current view month & year in the calendar
  const [viewDate, setViewDate] = useState(() => selectedDate || new Date());

  // Sync view when opened or value changes
  useEffect(() => {
    if (selectedDate) {
      setViewDate(new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1));
    }
  }, [value, open]);

  useEffect(() => {
    if (!open) return;
    const handleClick = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    const handleKey = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handleClick);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handleClick);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);

  const viewYear = viewDate.getFullYear();
  const viewMonth = viewDate.getMonth();

  const handlePrevMonth = (e) => {
    e.stopPropagation();
    setViewDate(new Date(viewYear, viewMonth - 1, 1));
  };

  const handleNextMonth = (e) => {
    e.stopPropagation();
    setViewDate(new Date(viewYear, viewMonth + 1, 1));
  };

  const handleSelectDay = (day) => {
    const nextDate = new Date(viewYear, viewMonth, day);
    onChange(formatToIso(nextDate));
    setOpen(false);
  };

  const handleToday = (e) => {
    e.stopPropagation();
    const today = new Date();
    onChange(formatToIso(today));
    setViewDate(new Date(today.getFullYear(), today.getMonth(), 1));
    setOpen(false);
  };

  const handleClear = (e) => {
    e.stopPropagation();
    onChange('');
    setOpen(false);
  };

  // Generate days grid for viewMonth/viewYear
  const calendarDays = useMemo(() => {
    const firstDayIndex = new Date(viewYear, viewMonth, 1).getDay();
    const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
    const daysInPrevMonth = new Date(viewYear, viewMonth, 0).getDate();

    const days = [];

    // Prev month padding
    for (let i = firstDayIndex - 1; i >= 0; i--) {
      days.push({
        day: daysInPrevMonth - i,
        isCurrentMonth: false,
        isPrev: true,
      });
    }

    // Current month days
    for (let d = 1; d <= daysInMonth; d++) {
      days.push({
        day: d,
        isCurrentMonth: true,
      });
    }

    // Next month padding (to reach 35 or 42 cells)
    const remaining = (7 - (days.length % 7)) % 7;
    for (let i = 1; i <= remaining; i++) {
      days.push({
        day: i,
        isCurrentMonth: false,
        isNext: true,
      });
    }

    return days;
  }, [viewYear, viewMonth]);

  const todayIso = formatToIso(new Date());

  // Year options for quick select (e.g., past 10 years to next 5 years)
  const currentYear = new Date().getFullYear();
  const years = useMemo(() => {
    const list = [];
    for (let y = currentYear - 15; y <= currentYear + 5; y++) {
      list.push(y);
    }
    return list;
  }, [currentYear]);

  return (
    <div ref={ref} className={`relative inline-block ${className}`}>
      {/* Trigger Button */}
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
        className={`flex items-center gap-2 px-2.5 py-1.5 text-xs bg-surface border border-border-default rounded-lg text-text-primary hover:border-border-strong focus:outline-none focus:ring-1 focus:ring-brand focus:border-brand transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${buttonClassName}`}
        title={value ? `Selected: ${value}` : 'Pick a date'}
      >
        <CalendarIcon className="w-3.5 h-3.5 text-text-muted shrink-0" />
        <span className={`font-mono ${value ? 'text-text-primary font-medium' : 'text-text-muted'}`}>
          {value ? formatDisplayDate(value) : placeholder}
        </span>
        {value && (
          <span
            onClick={handleClear}
            title="Clear date"
            className="p-0.5 rounded hover:bg-surface-secondary text-text-muted hover:text-danger cursor-pointer ml-auto"
          >
            <X className="w-3 h-3" />
          </span>
        )}
      </button>

      {/* Popover Calendar */}
      {open && (
        <div className="absolute top-full left-0 mt-1.5 z-50 bg-surface border border-border-default rounded-xl shadow-xl p-3 w-64 select-none animate-scale-in">
          {/* Calendar Header */}
          <div className="flex items-center justify-between mb-2.5 pb-2 border-b border-border-default">
            <button
              type="button"
              onClick={handlePrevMonth}
              className="p-1 rounded-lg text-text-secondary hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
              title="Previous Month"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>

            <div className="flex items-center gap-1">
              <select
                value={viewMonth}
                onChange={(e) => setViewDate(new Date(viewYear, parseInt(e.target.value, 10), 1))}
                className="bg-surface font-semibold text-xs text-text-primary border-0 rounded p-1 cursor-pointer focus:outline-none"
              >
                {MONTH_NAMES.map((m, idx) => (
                  <option key={m} value={idx}>
                    {m}
                  </option>
                ))}
              </select>

              <select
                value={viewYear}
                onChange={(e) => setViewDate(new Date(parseInt(e.target.value, 10), viewMonth, 1))}
                className="bg-surface font-semibold text-xs text-text-primary border-0 rounded p-1 cursor-pointer focus:outline-none font-mono"
              >
                {years.map((y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="button"
              onClick={handleNextMonth}
              className="p-1 rounded-lg text-text-secondary hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
              title="Next Month"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>

          {/* Weekday headers */}
          <div className="grid grid-cols-7 gap-1 text-center mb-1">
            {SHORT_DAYS.map((d) => (
              <span key={d} className="text-[10px] font-bold text-text-muted uppercase">
                {d}
              </span>
            ))}
          </div>

          {/* Days Grid */}
          <div className="grid grid-cols-7 gap-1 text-center">
            {calendarDays.map((cell, idx) => {
              if (!cell.isCurrentMonth) {
                return (
                  <span
                    key={`empty-${idx}`}
                    className="w-7 h-7 text-xs flex items-center justify-center text-text-muted/40 font-mono"
                  >
                    {cell.day}
                  </span>
                );
              }

              const thisDateIso = `${viewYear}-${String(viewMonth + 1).padStart(2, '0')}-${String(
                cell.day
              ).padStart(2, '0')}`;
              const isSelected = value === thisDateIso;
              const isToday = thisDateIso === todayIso;

              return (
                <button
                  key={`day-${cell.day}`}
                  type="button"
                  onClick={() => handleSelectDay(cell.day)}
                  className={`w-7 h-7 text-xs rounded-lg flex items-center justify-center font-mono transition-colors cursor-pointer ${
                    isSelected
                      ? 'bg-brand text-white font-bold shadow-xs'
                      : isToday
                      ? 'bg-brand-subtle text-brand font-bold border border-brand/40'
                      : 'text-text-primary hover:bg-surface-secondary'
                  }`}
                >
                  {cell.day}
                </button>
              );
            })}
          </div>

          {/* Footer Shortcuts */}
          <div className="flex items-center justify-between mt-2.5 pt-2 border-t border-border-default text-xs">
            <button
              type="button"
              onClick={handleToday}
              className="px-2 py-0.5 rounded text-[11px] font-semibold text-brand hover:bg-brand-subtle transition-colors cursor-pointer"
            >
              Today
            </button>
            {value && (
              <button
                type="button"
                onClick={handleClear}
                className="px-2 py-0.5 rounded text-[11px] font-medium text-danger hover:bg-danger-bg transition-colors cursor-pointer"
              >
                Clear
              </button>
            )}
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="px-2 py-0.5 rounded text-[11px] font-medium text-text-muted hover:text-text-primary transition-colors cursor-pointer"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Pre-built Date Range Selector (From & To)
 */
export function DateRangePicker({
  label = 'Date Range',
  fromVal = '',
  toVal = '',
  onFromChange,
  onToChange,
  onClear,
}) {
  return (
    <div className="flex items-center gap-1.5 text-xs text-text-secondary flex-wrap">
      {label && <span className="font-medium text-text-primary whitespace-nowrap">{label}:</span>}
      <div className="flex items-center gap-1">
        <span className="text-[11px] text-text-muted">From</span>
        <DatePicker
          value={fromVal}
          onChange={onFromChange}
          placeholder="Start date"
        />
      </div>
      <span className="text-text-muted">–</span>
      <div className="flex items-center gap-1">
        <span className="text-[11px] text-text-muted">To</span>
        <DatePicker
          value={toVal}
          onChange={onToChange}
          placeholder="End date"
        />
      </div>
      {(fromVal || toVal) && onClear && (
        <button
          type="button"
          onClick={onClear}
          className="text-xs text-brand hover:underline px-1 cursor-pointer"
        >
          Clear
        </button>
      )}
    </div>
  );
}

