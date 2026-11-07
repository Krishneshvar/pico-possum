import { useState, useMemo, useRef, useEffect } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ChevronsUpDown,
  ArrowUp,
  ArrowDown,
  X,
  Check,
  GripVertical,
} from 'lucide-react';
import { nextSortState, sortRows } from '../../lib/sort';

// ── Persisted Table State (column order & widths) ────────────────────────────
const TABLE_STATE_PREFIX = 'pico-possum:table-state:';

function readTableState(key) {
  if (!key || typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(TABLE_STATE_PREFIX + key);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return {
      order: Array.isArray(parsed?.order)
        ? parsed.order.filter((k) => typeof k === 'string')
        : null,
      widths:
        parsed?.widths && typeof parsed.widths === 'object' && !Array.isArray(parsed.widths)
          ? parsed.widths
          : {},
    };
  } catch {
    return null;
  }
}

function writeTableState(key, state) {
  if (!key || typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(TABLE_STATE_PREFIX + key, JSON.stringify(state));
  } catch {
    // Best-effort storage
  }
}

// ── Sorting helpers ──────────────────────────────────────────────────────────
function getSortValue(row, col) {
  if (typeof col.sortValue === 'function') return col.sortValue(row);
  if (col.sortKey) return row[col.sortKey];
  return row[col.key];
}

// ── MultiSelect Dropdown ────────────────────────────────────────────────────
function MultiSelectDropdown({ label, options, selected, onChange }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const toggle = (value) => {
    const next = selected.includes(value)
      ? selected.filter((v) => v !== value)
      : [...selected, value];
    onChange(next);
  };

  const activeCount = selected.length;

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className={`flex items-center gap-1.5 bg-surface border rounded-lg px-2.5 py-1.5 text-xs transition-colors cursor-pointer ${
          activeCount > 0
            ? 'border-brand text-brand font-semibold shadow-2xs'
            : 'border-border-default text-text-secondary hover:border-border-strong'
        }`}
      >
        <span>{activeCount > 0 ? `${label} (${activeCount})` : label}</span>
        <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1 z-50 bg-surface border border-border-default rounded-xl shadow-lg min-w-[180px] overflow-hidden animate-scale-in">
          {options.map((o) => {
            const isSelected = selected.includes(o.value);
            return (
              <button
                key={o.value}
                type="button"
                onClick={() => toggle(o.value)}
                className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs text-left transition-colors cursor-pointer ${
                  isSelected
                    ? 'bg-brand-subtle text-brand font-medium'
                    : 'text-text-secondary hover:bg-surface-secondary hover:text-text-primary'
                }`}
              >
                <span
                  className={`w-3.5 h-3.5 rounded border flex items-center justify-center shrink-0 ${
                    isSelected ? 'bg-brand border-brand' : 'border-border-strong'
                  }`}
                >
                  {isSelected && <Check className="w-2.5 h-2.5 text-white" strokeWidth={3} />}
                </span>
                <span className="truncate">{o.label}</span>
              </button>
            );
          })}
          {activeCount > 0 && (
            <button
              type="button"
              onClick={() => {
                onChange([]);
                setOpen(false);
              }}
              className="w-full flex items-center gap-2 px-3 py-2 text-[11px] text-danger hover:bg-danger-bg border-t border-border-default transition-colors cursor-pointer"
            >
              <X className="w-3 h-3" /> Clear selected
            </button>
          )}
        </div>
      )}
    </div>
  );
}

// ── Range Filter (number) ────────────────────────────────────────────────────
function RangeFilter({ label, unit, minVal, maxVal, onMinChange, onMaxChange }) {
  return (
    <div className="flex items-center gap-1.5 text-xs text-text-secondary">
      <span className="whitespace-nowrap">{label}:</span>
      <input
        type="number"
        placeholder={`Min${unit ? ` (${unit})` : ''}`}
        value={minVal}
        onChange={(e) => onMinChange(e.target.value)}
        className="w-20 bg-surface border border-border-default text-text-primary rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-brand font-mono transition-colors"
      />
      <span className="text-text-muted">–</span>
      <input
        type="number"
        placeholder={`Max${unit ? ` (${unit})` : ''}`}
        value={maxVal}
        onChange={(e) => onMaxChange(e.target.value)}
        className="w-20 bg-surface border border-border-default text-text-primary rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-brand font-mono transition-colors"
      />
    </div>
  );
}

// ── Date Range Filter ────────────────────────────────────────────────────────
function DateRangeFilter({ label, fromVal, toVal, onFromChange, onToChange }) {
  return (
    <div className="flex items-center gap-1.5 text-xs text-text-secondary">
      <span className="whitespace-nowrap">{label}:</span>
      <input
        type="date"
        value={fromVal}
        onChange={(e) => onFromChange(e.target.value)}
        className="bg-surface border border-border-default text-text-primary rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-brand font-mono transition-colors"
      />
      <span className="text-text-muted">–</span>
      <input
        type="date"
        value={toVal}
        onChange={(e) => onToChange(e.target.value)}
        className="bg-surface border border-border-default text-text-primary rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-brand font-mono transition-colors"
      />
    </div>
  );
}

// ── Main DataTable ───────────────────────────────────────────────────────────
export default function DataTable({
  columns = [],
  data = [],
  keyFn,
  searchKeys,
  filters = [],
  pageSize = 20,
  emptyMessage = 'No records found.',
  actions,
  isLoading = false,
  headerRight,
  onRowClick,
  onRowDoubleClick,
  persistKey,
  onFilteredChange,
  selectedRowId = null,
}) {
  const [search, setSearch] = useState('');
  const [selectValues, setSelectValues] = useState({});
  const [multiValues, setMultiValues] = useState({});
  const [rangeValues, setRangeValues] = useState({});
  const [dateValues, setDateValues] = useState({});
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState({ key: null, dir: null });

  // ── Column Ordering State (persisted per table) ────────────────────────────
  const persistedTableState = useMemo(() => readTableState(persistKey), [persistKey]);

  const [colOrder, setColOrder] = useState(() => {
    const currentKeys = columns.map((c) => c.key);
    const savedOrder = persistedTableState?.order ?? [];
    const kept = savedOrder.filter((k) => currentKeys.includes(k));
    return [...kept, ...currentKeys.filter((k) => !kept.includes(k))];
  });
  const [draggedColKey, setDraggedColKey] = useState(null);
  const [dragOverColKey, setDragOverColKey] = useState(null);

  // ── Column Resizing State (persisted per table) ────────────────────────────
  const [colWidths, setColWidths] = useState(() => {
    const savedWidths = persistedTableState?.widths ?? {};
    const validWidths = {};
    for (const col of columns) {
      const width = savedWidths[col.key];
      if (typeof width === 'number' && Number.isFinite(width) && width > 0) {
        validWidths[col.key] = width;
      }
    }
    return validWidths;
  });
  const isResizing = useRef(false);
  const suppressHeaderClick = useRef(false);

  // Sync colOrder if columns change
  useEffect(() => {
    const currentKeys = columns.map((c) => c.key);
    setColOrder((prev) => {
      const filteredPrev = prev.filter((k) => currentKeys.includes(k));
      const newKeys = currentKeys.filter((k) => !filteredPrev.includes(k));
      const next = [...filteredPrev, ...newKeys];
      const unchanged =
        next.length === prev.length && next.every((key, i) => key === prev[i]);
      return unchanged ? prev : next;
    });
  }, [columns]);

  // Persist column order/widths
  useEffect(() => {
    if (!persistKey) return;
    const timer = setTimeout(() => {
      writeTableState(persistKey, { order: colOrder, widths: colWidths });
    }, 200);
    return () => clearTimeout(timer);
  }, [persistKey, colOrder, colWidths]);

  const orderedColumns = useMemo(() => {
    const colMap = new Map(columns.map((c) => [c.key, c]));
    return colOrder.map((key) => colMap.get(key)).filter(Boolean);
  }, [colOrder, columns]);

  // ── Drag & Drop Column Reordering ──────────────────────────────────────────
  const handleDragStart = (e, colKey) => {
    if (isResizing.current) {
      e.preventDefault();
      return;
    }
    suppressHeaderClick.current = true;
    setDraggedColKey(colKey);
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', colKey);
  };

  const handleDragOver = (e, targetKey) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (dragOverColKey !== targetKey) {
      setDragOverColKey(targetKey);
    }
  };

  const handleDrop = (e, targetKey) => {
    e.preventDefault();
    if (!draggedColKey || draggedColKey === targetKey) {
      setDraggedColKey(null);
      setDragOverColKey(null);
      return;
    }

    setColOrder((prev) => {
      const next = [...prev];
      const fromIdx = next.indexOf(draggedColKey);
      const toIdx = next.indexOf(targetKey);
      if (fromIdx === -1 || toIdx === -1) return prev;
      next.splice(fromIdx, 1);
      next.splice(toIdx, 0, draggedColKey);
      return next;
    });

    setDraggedColKey(null);
    setDragOverColKey(null);
  };

  const handleDragEnd = () => {
    setDraggedColKey(null);
    setDragOverColKey(null);
    setTimeout(() => {
      suppressHeaderClick.current = false;
    }, 250);
  };

  // ── Column Resizing Drag Handler ───────────────────────────────────────────
  const startResizing = (e, colKey) => {
    e.preventDefault();
    e.stopPropagation();
    isResizing.current = true;

    const startX = e.clientX;
    const thEl = e.currentTarget.parentElement;
    const startWidth = thEl ? thEl.getBoundingClientRect().width : (colWidths[colKey] || 120);

    const onMouseMove = (moveEvent) => {
      const delta = moveEvent.clientX - startX;
      const newWidth = Math.max(50, Math.round(startWidth + delta));
      setColWidths((prev) => ({
        ...prev,
        [colKey]: newWidth,
      }));
    };

    const onMouseUp = () => {
      isResizing.current = false;
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  };

  // ── Filter change handlers ──────────────────────────────────────────────────
  const handleSearch = (val) => {
    setSearch(val);
    setPage(1);
  };

  const handleSelect = (key, val) => {
    setSelectValues((p) => ({ ...p, [key]: val }));
    setPage(1);
  };

  const handleMulti = (key, vals) => {
    setMultiValues((p) => ({ ...p, [key]: vals }));
    setPage(1);
  };

  const handleRangeMin = (key, val) => {
    setRangeValues((p) => ({ ...p, [key]: { ...p[key], min: val } }));
    setPage(1);
  };

  const handleRangeMax = (key, val) => {
    setRangeValues((p) => ({ ...p, [key]: { ...p[key], max: val } }));
    setPage(1);
  };

  const dateRangeKey = (f) => f.key ?? `${f.fromKey}__${f.toKey}`;

  const handleDateFrom = (dk, val) => {
    setDateValues((p) => ({ ...p, [dk]: { ...p[dk], from: val } }));
    setPage(1);
  };

  const handleDateTo = (dk, val) => {
    setDateValues((p) => ({ ...p, [dk]: { ...p[dk], to: val } }));
    setPage(1);
  };

  // Enrich filter options with record counts from `data`
  const enrichedFilters = useMemo(() => {
    if (!filters || filters.length === 0) return [];
    return filters.map((f) => {
      if ((f.type === 'select' || f.type === 'multiselect') && Array.isArray(f.options)) {
        const filterFn = f.filterFn || f.predicate;
        const newOptions = f.options.map((opt) => {
          let count = 0;
          if (typeof filterFn === 'function') {
            count = data.filter((row) => filterFn(row, [opt.value])).length;
          } else {
            count = data.filter((row) => String(row[f.key]) === String(opt.value)).length;
          }
          const baseLabel = String(opt.label).replace(/\s*\(\d+\)$/, '');
          return {
            ...opt,
            label: `${baseLabel} (${count})`,
          };
        });
        return { ...f, options: newOptions };
      }
      return f;
    });
  }, [filters, data]);

  // ── Filter pipeline ────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    let result = data;

    // Global text search across searchKeys
    if (search.trim()) {
      const q = search.trim().toLowerCase();
      result = result.filter((row) => {
        if (!searchKeys || searchKeys.length === 0) {
          return Object.values(row).some((v) =>
            v !== null && v !== undefined && String(v).toLowerCase().includes(q)
          );
        }
        return searchKeys.some((k) => {
          const v = row[k];
          return v !== null && v !== undefined && String(v).toLowerCase().includes(q);
        });
      });
    }

    // Single-select filters
    for (const [key, val] of Object.entries(selectValues)) {
      if (val) {
        const filterDef = filters.find((flt) => flt.key === key);
        if (filterDef && typeof filterDef.filterFn === 'function') {
          result = result.filter((row) => filterDef.filterFn(row, val));
        } else {
          result = result.filter((row) => String(row[key]) === String(val));
        }
      }
    }

    // Multi-select filters
    for (const [key, vals] of Object.entries(multiValues)) {
      if (vals && vals.length > 0) {
        const filterDef = filters.find((flt) => flt.key === key);
        if (filterDef && typeof filterDef.filterFn === 'function') {
          result = result.filter((row) => filterDef.filterFn(row, vals));
        } else {
          result = result.filter((row) => vals.map(String).includes(String(row[key])));
        }
      }
    }

    // Range (number) filters
    for (const f of filters.filter((flt) => (flt.type ?? 'select') === 'range')) {
      const rv = rangeValues[f.key];
      if (!rv) continue;
      const { min, max } = rv;
      if (min !== '' && min !== undefined) {
        const minNum = parseFloat(min);
        if (!isNaN(minNum)) {
          result = result.filter((row) => {
            const raw = f.transform ? f.transform(row[f.key]) : row[f.key];
            return parseFloat(raw) >= minNum;
          });
        }
      }
      if (max !== '' && max !== undefined) {
        const maxNum = parseFloat(max);
        if (!isNaN(maxNum)) {
          result = result.filter((row) => {
            const raw = f.transform ? f.transform(row[f.key]) : row[f.key];
            return parseFloat(raw) <= maxNum;
          });
        }
      }
    }

    // Date range filters
    for (const f of filters.filter((flt) => flt.type === 'daterange')) {
      const dk = dateRangeKey(f);
      const dv = dateValues[dk];
      if (!dv) continue;
      const { from, to } = dv;
      if (from) {
        result = result.filter((row) => {
          const val = row[f.fromKey ?? f.key];
          if (!val) return false;
          return new Date(val) >= new Date(from);
        });
      }
      if (to) {
        result = result.filter((row) => {
          const val = row[f.toKey ?? f.key];
          if (!val) return false;
          const end = new Date(to);
          end.setHours(23, 59, 59, 999);
          return new Date(val) <= end;
        });
      }
    }

    return result;
  }, [data, search, searchKeys, selectValues, multiValues, rangeValues, dateValues, filters]);

  // Notify parent of filtered dataset if requested
  useEffect(() => {
    if (onFilteredChange) onFilteredChange(filtered);
  }, [filtered, onFilteredChange]);

  // ── Sort pipeline ──────────────────────────────────────────────────────────
  const sorted = useMemo(() => {
    if (!sort.key || !sort.dir) return filtered;
    const col = columns.find((c) => c.key === sort.key);
    return sortRows(filtered, sort, (row) => (col ? getSortValue(row, col) : row[sort.key]));
  }, [filtered, sort, columns]);

  // ── Pagination ─────────────────────────────────────────────────────────────
  const totalPages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const safePage = Math.min(page, totalPages);
  const startIdx = (safePage - 1) * pageSize;
  const slice = sorted.slice(startIdx, startIdx + pageSize);

  const goPage = (p) => setPage(Math.max(1, Math.min(p, totalPages)));

  const toggleSort = (colKey) => {
    setSort((prev) => nextSortState(prev, colKey));
    setPage(1);
  };

  const hasActiveFilters =
    search.trim() ||
    Object.values(selectValues).some(Boolean) ||
    Object.values(multiValues).some((v) => v && v.length > 0) ||
    Object.values(rangeValues).some((v) => v?.min || v?.max) ||
    Object.values(dateValues).some((v) => v?.from || v?.to);

  const clearAllFilters = () => {
    setSearch('');
    setSelectValues({});
    setMultiValues({});
    setRangeValues({});
    setDateValues({});
    setPage(1);
  };

  return (
    <div className="flex flex-col h-full overflow-hidden bg-surface">
      {/* ── Standardized Toolbar ── */}
      <div className="flex items-center gap-2.5 px-4 py-2 border-b border-border-default bg-surface shrink-0 flex-wrap">
        {/* Search Input */}
        <div className="relative flex-1 min-w-44 max-w-xs">
          <svg
            className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-text-muted pointer-events-none"
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
            placeholder="Search records…"
            className="w-full pl-8 pr-3 py-1.5 bg-surface-secondary border border-border-default rounded-lg text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
          />
        </div>

        {/* Dynamic Filters */}
        {enrichedFilters.map((f) => {
          const type = f.type ?? 'select';

          if (type === 'select') {
            return (
              <select
                key={f.key}
                value={selectValues[f.key] ?? ''}
                onChange={(e) => handleSelect(f.key, e.target.value)}
                className={`bg-surface border text-xs rounded-lg px-2.5 py-1.5 focus:outline-none focus:border-brand transition-colors cursor-pointer ${
                  selectValues[f.key]
                    ? 'border-brand text-brand font-semibold shadow-2xs'
                    : 'border-border-default text-text-secondary hover:border-border-strong'
                }`}
              >
                <option value="">{f.label}</option>
                {f.options.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
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
            return (
              <DateRangeFilter
                key={dk}
                label={f.label}
                fromVal={dv.from ?? ''}
                toVal={dv.to ?? ''}
                onFromChange={(v) => handleDateFrom(dk, v)}
                onToChange={(v) => handleDateTo(dk, v)}
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
            <X className="w-3 h-3" /> Clear filters
          </button>
        )}

        <div className="flex-1" />
        {typeof headerRight === 'function' ? headerRight({ filtered }) : headerRight}
      </div>

      {/* ── Table Container ── */}
      <div className="flex-1 overflow-x-auto overflow-y-auto">
        <table className="w-full text-left border-collapse select-text">
          <thead className="bg-table-header-bg text-[11px] font-semibold text-table-header-text uppercase tracking-wider sticky top-0 z-10 border-b border-table-header-border shadow-2xs">
            <tr className="h-10">
              {orderedColumns.map((col) => {
                const widthStyle = colWidths[col.key]
                  ? { width: `${colWidths[col.key]}px`, minWidth: `${colWidths[col.key]}px` }
                  : col.minWidth
                  ? { minWidth: `${col.minWidth}px` }
                  : {};

                const isDropTarget = dragOverColKey === col.key && draggedColKey !== col.key;
                const isDraggingThis = draggedColKey === col.key;
                const isSortable = col.sortable !== false;
                const isSorted = isSortable && sort.key === col.key && !!sort.dir;
                const SortIcon =
                  sort.dir === 'asc' && sort.key === col.key
                    ? ArrowUp
                    : sort.dir === 'desc' && sort.key === col.key
                    ? ArrowDown
                    : ChevronsUpDown;

                return (
                  <th
                    key={col.key}
                    draggable
                    onDragStart={(e) => handleDragStart(e, col.key)}
                    onDragOver={(e) => handleDragOver(e, col.key)}
                    onDrop={(e) => handleDrop(e, col.key)}
                    onDragEnd={handleDragEnd}
                    onClick={() => {
                      if (!isSortable || suppressHeaderClick.current) return;
                      toggleSort(col.key);
                    }}
                    title={isSortable ? `Sort by ${col.label}` : undefined}
                    aria-sort={isSorted ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
                    style={widthStyle}
                    className={`relative py-2.5 px-3.5 transition-colors select-none group/th ${
                      isSortable ? 'cursor-pointer hover:bg-white/10' : ''
                    } ${col.headerClassName ?? ''} ${isDraggingThis ? 'opacity-30' : ''} ${
                      isDropTarget ? 'bg-brand-subtle border-l-2 border-brand' : ''
                    }`}
                  >
                    <div className="flex items-center gap-1.5 overflow-hidden">
                      <GripVertical className="w-3 h-3 text-table-header-text/40 opacity-40 group-hover/th:opacity-100 transition-opacity shrink-0 cursor-grab active:cursor-grabbing" />
                      <span className="truncate">{col.label}</span>
                      {isSortable && (
                        <span
                          className={`ml-auto shrink-0 flex items-center transition-opacity ${
                            isSorted ? 'text-brand opacity-100 font-bold' : 'text-table-header-text/40 opacity-0 group-hover/th:opacity-100'
                          }`}
                        >
                          <SortIcon className="w-3 h-3" strokeWidth={isSorted ? 2.5 : 1.5} />
                        </span>
                      )}
                    </div>

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
                      title="Drag to resize, double-click to reset"
                      className="absolute top-0 right-0 bottom-0 w-3 -mr-1.5 cursor-col-resize select-none flex items-center justify-center group/resize z-20"
                    >
                      <div className="w-[1.5px] h-3.5 bg-border-strong group-hover/resize:bg-brand group-hover/resize:w-[2px] group-hover/resize:h-full transition-all" />
                    </div>
                  </th>
                );
              })}
              {actions && (
                <th className="py-2.5 px-3.5 text-right sticky right-0 bg-table-header-bg z-10 w-28 text-table-header-text">
                  Actions
                </th>
              )}
            </tr>
          </thead>
          <tbody className="divide-y divide-border-default text-xs">
            {isLoading ? (
              <tr>
                <td colSpan={orderedColumns.length + (actions ? 1 : 0)} className="py-16 text-center text-text-muted">
                  <div className="inline-block w-5 h-5 border-2 border-border-default border-t-brand rounded-full animate-spin" />
                </td>
              </tr>
            ) : slice.length === 0 ? (
              <tr>
                <td colSpan={orderedColumns.length + (actions ? 1 : 0)} className="py-16 text-center text-text-muted">
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              slice.map((row) => {
                const rowKey = keyFn(row);
                const isSelected = selectedRowId != null && selectedRowId === rowKey;
                return (
                  <tr
                    key={rowKey}
                    onClick={() => onRowClick && onRowClick(row)}
                    onDoubleClick={() => onRowDoubleClick && onRowDoubleClick(row)}
                    className={`h-11 transition-colors ${
                      isSelected
                        ? 'bg-brand-subtle'
                        : 'hover:bg-surface-secondary/70'
                    } ${onRowClick || onRowDoubleClick ? 'cursor-pointer' : ''}`}
                  >
                    {orderedColumns.map((col) => {
                      const widthStyle = colWidths[col.key]
                        ? { width: `${colWidths[col.key]}px`, minWidth: `${colWidths[col.key]}px` }
                        : col.minWidth
                        ? { minWidth: `${col.minWidth}px` }
                        : {};

                      return (
                        <td
                          key={col.key}
                          style={widthStyle}
                          className={`py-2 px-3.5 ${col.className ?? ''}`}
                        >
                          {col.render ? col.render(row) : (row[col.key] ?? '—')}
                        </td>
                      );
                    })}
                    {actions && (
                      <td className="py-2 px-3.5 text-right sticky right-0 bg-surface/95 z-0">
                        {actions(row)}
                      </td>
                    )}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* ── Standardized Pagination ── */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-4 py-2 border-t border-border-default bg-surface-secondary shrink-0 text-xs text-text-secondary">
          <span>
            Page <strong className="text-text-primary font-semibold">{safePage}</strong> of{' '}
            <strong className="text-text-primary font-semibold">{totalPages}</strong>
          </span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => goPage(1)}
              disabled={safePage === 1}
              className="px-2 py-1 rounded font-bold disabled:opacity-30 hover:bg-surface transition-colors cursor-pointer"
              title="First page"
            >
              «
            </button>
            <button
              type="button"
              onClick={() => goPage(safePage - 1)}
              disabled={safePage === 1}
              className="p-1 rounded disabled:opacity-30 hover:bg-surface transition-colors cursor-pointer"
              title="Previous page"
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            {Array.from({ length: totalPages }, (_, i) => i + 1)
              .filter((p) => Math.abs(p - safePage) <= 2)
              .map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => goPage(p)}
                  className={`w-7 h-7 rounded font-semibold text-xs transition-colors cursor-pointer ${
                    p === safePage
                      ? 'bg-brand text-white shadow-xs'
                      : 'hover:bg-surface text-text-secondary'
                  }`}
                >
                  {p}
                </button>
              ))}
            <button
              type="button"
              onClick={() => goPage(safePage + 1)}
              disabled={safePage === totalPages}
              className="p-1 rounded disabled:opacity-30 hover:bg-surface transition-colors cursor-pointer"
              title="Next page"
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
            <button
              type="button"
              onClick={() => goPage(totalPages)}
              disabled={safePage === totalPages}
              className="px-2 py-1 rounded font-bold disabled:opacity-30 hover:bg-surface transition-colors cursor-pointer"
              title="Last page"
            >
              »
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
