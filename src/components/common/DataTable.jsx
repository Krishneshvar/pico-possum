import { useState, useMemo, useRef, useEffect } from 'react';
import { ChevronLeft, ChevronRight, ChevronDown, X, Check, GripVertical } from 'lucide-react';

/**
 * Generic reusable DataTable component.
 *
 * Features:
 *   - Column reordering via drag-and-drop
 *   - Column resizing via edge drag handles
 *   - Search input across defined keys
 *   - Filters: select, multiselect, range (min/max), daterange (from/to)
 *   - Client-side pagination with row counts
 *   - onRowClick / onRowDoubleClick handlers
 *
 * FilterDef shapes:
 *   Single select:  { type: 'select',      key, label, options: [{value,label}] }
 *   Multi-select:   { type: 'multiselect', key, label, options: [{value,label}] }
 *   Number range:   { type: 'range',       key, label, unit?, transform?: (rawVal)=>number }
 *   Date range:     { type: 'daterange',   fromKey, toKey, label }
 */

// ── MultiSelect Dropdown ────────────────────────────────────────────────────
function MultiSelectDropdown({ label, options, selected, onChange }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
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
        className={`flex items-center gap-1.5 bg-slate-900 border rounded-lg px-2.5 py-1.5 text-xs focus:outline-none transition-colors ${
          activeCount > 0
            ? 'border-emerald-500/60 text-emerald-400'
            : 'border-slate-800 text-slate-300 hover:border-slate-600'
        }`}
      >
        <span>{activeCount > 0 ? `${label} (${activeCount})` : label}</span>
        <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1 z-50 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl shadow-black/50 min-w-[170px] overflow-hidden">
          {options.map((o) => {
            const isSelected = selected.includes(o.value);
            return (
              <button
                key={o.value}
                type="button"
                onClick={() => toggle(o.value)}
                className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs text-left transition-colors ${
                  isSelected
                    ? 'bg-emerald-500/10 text-emerald-300'
                    : 'text-slate-300 hover:bg-slate-800'
                }`}
              >
                <span className={`w-3.5 h-3.5 rounded border flex items-center justify-center flex-shrink-0 ${
                  isSelected ? 'bg-emerald-500 border-emerald-500' : 'border-slate-600'
                }`}>
                  {isSelected && <Check className="w-2.5 h-2.5 text-slate-950" strokeWidth={3} />}
                </span>
                {o.label}
              </button>
            );
          })}
          {activeCount > 0 && (
            <button
              type="button"
              onClick={() => { onChange([]); setOpen(false); }}
              className="w-full flex items-center gap-2 px-3 py-2 text-[11px] text-rose-400 hover:bg-rose-500/10 border-t border-slate-800 transition-colors"
            >
              <X className="w-3 h-3" /> Clear all
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
    <div className="flex items-center gap-1.5">
      <span className="text-slate-500 text-xs whitespace-nowrap">{label}:</span>
      <input
        type="number"
        placeholder={`Min${unit ? ` (${unit})` : ''}`}
        value={minVal}
        onChange={(e) => onMinChange(e.target.value)}
        className="w-24 bg-slate-900 border border-slate-800 text-slate-300 rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-emerald-500 transition-colors font-mono"
      />
      <span className="text-slate-600 text-xs">–</span>
      <input
        type="number"
        placeholder={`Max${unit ? ` (${unit})` : ''}`}
        value={maxVal}
        onChange={(e) => onMaxChange(e.target.value)}
        className="w-24 bg-slate-900 border border-slate-800 text-slate-300 rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-emerald-500 transition-colors font-mono"
      />
    </div>
  );
}

// ── Date Range Filter ────────────────────────────────────────────────────────
function DateRangeFilter({ label, fromVal, toVal, onFromChange, onToChange }) {
  return (
    <div className="flex items-center gap-1.5">
      <span className="text-slate-500 text-xs whitespace-nowrap">{label}:</span>
      <input
        type="date"
        value={fromVal}
        onChange={(e) => onFromChange(e.target.value)}
        className="bg-slate-900 border border-slate-800 text-slate-300 rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-emerald-500 transition-colors font-mono"
      />
      <span className="text-slate-600 text-xs">–</span>
      <input
        type="date"
        value={toVal}
        onChange={(e) => onToChange(e.target.value)}
        className="bg-slate-900 border border-slate-800 text-slate-300 rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-emerald-500 transition-colors font-mono"
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
}) {
  const [search, setSearch] = useState('');
  const [selectValues, setSelectValues] = useState({});
  const [multiValues, setMultiValues] = useState({});
  const [rangeValues, setRangeValues] = useState({});
  const [dateValues, setDateValues] = useState({});
  const [page, setPage] = useState(1);

  // ── Column Ordering State ──────────────────────────────────────────────────
  const [colOrder, setColOrder] = useState(() => columns.map((c) => c.key));
  const [draggedColKey, setDraggedColKey] = useState(null);
  const [dragOverColKey, setDragOverColKey] = useState(null);

  // ── Column Resizing State ──────────────────────────────────────────────────
  const [colWidths, setColWidths] = useState({});
  const isResizing = useRef(false);

  // Sync colOrder if incoming columns prop changes
  useEffect(() => {
    setColOrder((prev) => {
      const currentKeys = columns.map((c) => c.key);
      const filteredPrev = prev.filter((k) => currentKeys.includes(k));
      const newKeys = currentKeys.filter((k) => !filteredPrev.includes(k));
      return [...filteredPrev, ...newKeys];
    });
  }, [columns]);

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

  const dateRangeKey = (f) => `${f.fromKey}__${f.toKey}`;

  // ── Filtering ────────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    let rows = data;

    for (const f of filters) {
      const type = f.type ?? 'select';

      if (type === 'select') {
        const val = selectValues[f.key];
        if (val) rows = rows.filter((r) => String(r[f.key]) === val);
      }

      if (type === 'multiselect') {
        const vals = multiValues[f.key] ?? [];
        if (vals.length > 0) rows = rows.filter((r) => vals.includes(String(r[f.key])));
      }

      if (type === 'range') {
        const rv = rangeValues[f.key] ?? {};
        const transform = f.transform ?? ((v) => parseFloat(v));
        const minN = rv.min !== '' && rv.min !== undefined ? transform(rv.min) : null;
        const maxN = rv.max !== '' && rv.max !== undefined ? transform(rv.max) : null;
        if (minN !== null && !isNaN(minN)) rows = rows.filter((r) => Number(r[f.key]) >= minN);
        if (maxN !== null && !isNaN(maxN)) rows = rows.filter((r) => Number(r[f.key]) <= maxN);
      }

      if (type === 'daterange') {
        const dk = dateRangeKey(f);
        const dv = dateValues[dk] ?? {};
        if (dv.from) {
          rows = rows.filter((r) => {
            const d = (r[f.fromKey] ?? '').slice(0, 10);
            return d >= dv.from;
          });
        }
        if (dv.to) {
          rows = rows.filter((r) => {
            const d = (r[f.fromKey] ?? '').slice(0, 10);
            return d <= dv.to;
          });
        }
      }
    }

    if (search.trim()) {
      const q = search.toLowerCase();
      const keys = searchKeys ?? columns.map((c) => c.key);
      rows = rows.filter((r) =>
        keys.some((k) => {
          const v = r[k];
          return v != null && String(v).toLowerCase().includes(q);
        })
      );
    }

    return rows;
  }, [data, search, selectValues, multiValues, rangeValues, dateValues, filters, searchKeys, columns]);

  // ── Pagination ───────────────────────────────────────────────────────────
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const safePage = Math.min(page, totalPages);
  const slice = filtered.slice((safePage - 1) * pageSize, safePage * pageSize);
  const goPage = (p) => setPage(Math.max(1, Math.min(totalPages, p)));

  const handleSearch = (v) => { setSearch(v); setPage(1); };
  const handleSelect = (key, val) => { setSelectValues((p) => ({ ...p, [key]: val })); setPage(1); };
  const handleMulti = (key, vals) => { setMultiValues((p) => ({ ...p, [key]: vals })); setPage(1); };
  const handleRangeMin = (key, val) => { setRangeValues((p) => ({ ...p, [key]: { ...(p[key] ?? {}), min: val } })); setPage(1); };
  const handleRangeMax = (key, val) => { setRangeValues((p) => ({ ...p, [key]: { ...(p[key] ?? {}), max: val } })); setPage(1); };
  const handleDateFrom = (dk, val) => { setDateValues((p) => ({ ...p, [dk]: { ...(p[dk] ?? {}), from: val } })); setPage(1); };
  const handleDateTo   = (dk, val) => { setDateValues((p) => ({ ...p, [dk]: { ...(p[dk] ?? {}), to: val } })); setPage(1); };

  const hasActiveFilters =
    Object.values(selectValues).some(Boolean) ||
    Object.values(multiValues).some((v) => v?.length > 0) ||
    Object.values(rangeValues).some((rv) => rv?.min || rv?.max) ||
    Object.values(dateValues).some((dv) => dv?.from || dv?.to);

  const clearAllFilters = () => {
    setSelectValues({});
    setMultiValues({});
    setRangeValues({});
    setDateValues({});
    setPage(1);
  };

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* ── Toolbar ── */}
      <div className="flex items-center gap-3 px-4 py-2.5 border-b border-slate-800/80 bg-slate-900/30 flex-shrink-0 flex-wrap gap-y-2">
        {/* Search */}
        <div className="relative flex-1 min-w-48 max-w-sm">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-500 pointer-events-none" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z" />
          </svg>
          <input
            type="text"
            value={search}
            onChange={(e) => handleSearch(e.target.value)}
            placeholder="Search…"
            className="w-full pl-8 pr-3 py-1.5 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-emerald-500 transition-colors"
          />
        </div>

        {/* Dynamic Filters */}
        {filters.map((f) => {
          const type = f.type ?? 'select';

          if (type === 'select') {
            return (
              <select
                key={f.key}
                value={selectValues[f.key] ?? ''}
                onChange={(e) => handleSelect(f.key, e.target.value)}
                className={`bg-slate-900 border text-xs rounded-lg px-2.5 py-1.5 focus:outline-none focus:border-emerald-500 transition-colors ${
                  selectValues[f.key] ? 'border-emerald-500/60 text-emerald-400' : 'border-slate-800 text-slate-300'
                }`}
              >
                <option value="">{f.label}</option>
                {f.options.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
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
            className="flex items-center gap-1 text-[11px] text-slate-400 hover:text-rose-400 transition-colors"
          >
            <X className="w-3 h-3" /> Clear filters
          </button>
        )}

        <div className="flex-1" />
        {headerRight}

        <span className="text-[11px] text-slate-500 whitespace-nowrap">
          {filtered.length} row{filtered.length !== 1 ? 's' : ''}
        </span>
      </div>

      {/* ── Table ── */}
      <div className="flex-1 overflow-x-auto overflow-y-auto">
        <table className="w-full text-left border-collapse select-text">
          <thead className="bg-slate-900/80 text-[11px] font-semibold text-slate-400 uppercase tracking-wider sticky top-0 z-10 border-b border-slate-800">
            <tr>
              {orderedColumns.map((col) => {
                const widthStyle = colWidths[col.key]
                  ? { width: `${colWidths[col.key]}px`, minWidth: `${colWidths[col.key]}px` }
                  : col.minWidth
                  ? { minWidth: `${col.minWidth}px` }
                  : {};

                const isDropTarget = dragOverColKey === col.key && draggedColKey !== col.key;
                const isDraggingThis = draggedColKey === col.key;

                return (
                  <th
                    key={col.key}
                    draggable
                    onDragStart={(e) => handleDragStart(e, col.key)}
                    onDragOver={(e) => handleDragOver(e, col.key)}
                    onDrop={(e) => handleDrop(e, col.key)}
                    onDragEnd={handleDragEnd}
                    style={widthStyle}
                    className={`relative py-3 px-4 transition-colors select-none group/th cursor-grab active:cursor-grabbing ${
                      col.headerClassName ?? ''
                    } ${isDraggingThis ? 'opacity-30' : ''} ${
                      isDropTarget ? 'bg-emerald-500/15 border-l-2 border-emerald-400' : ''
                    }`}
                  >
                    <div className="flex items-center gap-1.5 overflow-hidden">
                      <GripVertical className="w-3 h-3 text-slate-600 group-hover/th:text-slate-400 transition-colors shrink-0 opacity-40 group-hover/th:opacity-100" />
                      <span className="truncate">{col.label}</span>
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
                      <div className="w-[1.5px] h-4 bg-slate-700/80 group-hover/resize:bg-emerald-400 group-hover/resize:w-[2px] group-hover/resize:h-full transition-all" />
                    </div>
                  </th>
                );
              })}
              {actions && (
                <th className="py-3 px-4 text-right sticky right-0 bg-slate-900/80 z-10 w-28">
                  Actions
                </th>
              )}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/50 text-xs">
            {isLoading ? (
              <tr>
                <td colSpan={orderedColumns.length + (actions ? 1 : 0)} className="py-16 text-center text-slate-500">
                  <div className="inline-block w-5 h-5 border-2 border-slate-700 border-t-emerald-500 rounded-full animate-spin" />
                </td>
              </tr>
            ) : slice.length === 0 ? (
              <tr>
                <td colSpan={orderedColumns.length + (actions ? 1 : 0)} className="py-16 text-center text-slate-500">
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              slice.map((row) => (
                <tr
                  key={keyFn(row)}
                  onClick={() => onRowClick && onRowClick(row)}
                  onDoubleClick={() => onRowDoubleClick && onRowDoubleClick(row)}
                  className={`hover:bg-slate-900/50 transition-colors ${
                    onRowClick || onRowDoubleClick ? 'cursor-pointer' : ''
                  }`}
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
                        className={`py-3 px-4 ${col.className ?? ''}`}
                      >
                        {col.render ? col.render(row) : (row[col.key] ?? '-')}
                      </td>
                    );
                  })}
                  {actions && (
                    <td className="py-3 px-4 text-right sticky right-0 bg-slate-950/90 z-0">
                      {actions(row)}
                    </td>
                  )}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* ── Pagination ── */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-4 py-2.5 border-t border-slate-800/80 bg-slate-900/30 flex-shrink-0 text-xs text-slate-400">
          <span>
            Page <strong className="text-slate-200">{safePage}</strong> of <strong className="text-slate-200">{totalPages}</strong>
            <span className="ml-2 text-slate-500">({filtered.length} total)</span>
          </span>
          <div className="flex items-center gap-1">
            <button onClick={() => goPage(1)} disabled={safePage === 1} className="px-2 py-1 rounded font-bold disabled:opacity-30 hover:bg-slate-800 transition-colors" title="First page">«</button>
            <button onClick={() => goPage(safePage - 1)} disabled={safePage === 1} className="p-1 rounded disabled:opacity-30 hover:bg-slate-800 transition-colors" title="Previous page"><ChevronLeft className="w-3.5 h-3.5" /></button>
            {Array.from({ length: totalPages }, (_, i) => i + 1)
              .filter((p) => Math.abs(p - safePage) <= 2)
              .map((p) => (
                <button key={p} onClick={() => goPage(p)} className={`w-7 h-7 rounded font-semibold transition-colors ${p === safePage ? 'bg-emerald-500 text-slate-950' : 'hover:bg-slate-800 text-slate-400'}`}>{p}</button>
              ))}
            <button onClick={() => goPage(safePage + 1)} disabled={safePage === totalPages} className="p-1 rounded disabled:opacity-30 hover:bg-slate-800 transition-colors" title="Next page"><ChevronRight className="w-3.5 h-3.5" /></button>
            <button onClick={() => goPage(totalPages)} disabled={safePage === totalPages} className="px-2 py-1 rounded font-bold disabled:opacity-30 hover:bg-slate-800 transition-colors" title="Last page">»</button>
          </div>
        </div>
      )}
    </div>
  );
}
