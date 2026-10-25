import { useState, useMemo } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

/**
 * Generic reusable DataTable component.
 *
 * Props:
 *   columns: [{ key, label, render?, className?, headerClassName? }]
 *   data:    array of row objects
 *   keyFn:  (row) => unique key
 *   searchKeys: string[] – which fields to search (defaults to all string fields)
 *   filters: [{ key, label, options: [{value, label}] }] – optional select filters
 *   pageSize: number (default 20)
 *   emptyMessage: string
 *   actions: (row) => ReactNode  – right-most actions cell
 *   isLoading: bool
 */
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
  headerRight, // extra element to put right of search bar
}) {
  const [search, setSearch] = useState('');
  const [filterValues, setFilterValues] = useState({});
  const [page, setPage] = useState(1);

  // ── Filter & search ──────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    let rows = data;

    // Apply dynamic select filters
    for (const f of filters) {
      const val = filterValues[f.key];
      if (val) {
        rows = rows.filter((r) => String(r[f.key]) === String(val));
      }
    }

    // Search across searchKeys (or all string-valued columns)
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
  }, [data, search, filterValues, filters, searchKeys, columns]);

  // ── Pagination ───────────────────────────────────────────────────────────
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const safePage = Math.min(page, totalPages);
  const slice = filtered.slice((safePage - 1) * pageSize, safePage * pageSize);

  const goPage = (p) => setPage(Math.max(1, Math.min(totalPages, p)));

  // Reset to page 1 whenever filters/search change
  const handleSearch = (v) => { setSearch(v); setPage(1); };
  const handleFilter = (key, val) => { setFilterValues((prev) => ({ ...prev, [key]: val })); setPage(1); };

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* ── Toolbar ── */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-slate-800/80 bg-slate-900/30 flex-shrink-0 flex-wrap gap-y-2">
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

        {/* Dynamic select filters */}
        {filters.map((f) => (
          <select
            key={f.key}
            value={filterValues[f.key] ?? ''}
            onChange={(e) => handleFilter(f.key, e.target.value)}
            className="bg-slate-900 border border-slate-800 text-xs text-slate-300 rounded-lg px-2.5 py-1.5 focus:outline-none focus:border-emerald-500 transition-colors"
          >
            <option value="">{f.label}</option>
            {f.options.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        ))}

        <div className="flex-1" />
        {headerRight}

        {/* Row count */}
        <span className="text-[11px] text-slate-500 whitespace-nowrap">
          {filtered.length} row{filtered.length !== 1 ? 's' : ''}
        </span>
      </div>

      {/* ── Table ── */}
      <div className="flex-1 overflow-y-auto">
        <table className="w-full text-left border-collapse">
          <thead className="bg-slate-900/80 text-[11px] font-semibold text-slate-400 uppercase tracking-wider sticky top-0 z-10 border-b border-slate-800">
            <tr>
              {columns.map((col) => (
                <th key={col.key} className={`py-3 px-4 ${col.headerClassName ?? ''}`}>
                  {col.label}
                </th>
              ))}
              {actions && <th className="py-3 px-4 text-right">Actions</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/50 text-xs">
            {isLoading ? (
              <tr>
                <td colSpan={columns.length + (actions ? 1 : 0)} className="py-16 text-center text-slate-500">
                  <div className="inline-block w-5 h-5 border-2 border-slate-700 border-t-emerald-500 rounded-full animate-spin" />
                </td>
              </tr>
            ) : slice.length === 0 ? (
              <tr>
                <td colSpan={columns.length + (actions ? 1 : 0)} className="py-16 text-center text-slate-500">
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              slice.map((row) => (
                <tr key={keyFn(row)} className="hover:bg-slate-900/50 transition-colors">
                  {columns.map((col) => (
                    <td key={col.key} className={`py-3 px-4 ${col.className ?? ''}`}>
                      {col.render ? col.render(row) : (row[col.key] ?? '-')}
                    </td>
                  ))}
                  {actions && (
                    <td className="py-3 px-4 text-right">{actions(row)}</td>
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
            <button
              onClick={() => goPage(1)}
              disabled={safePage === 1}
              className="px-2 py-1 rounded font-bold disabled:opacity-30 hover:bg-slate-800 transition-colors"
              title="First page"
            >«</button>
            <button
              onClick={() => goPage(safePage - 1)}
              disabled={safePage === 1}
              className="p-1 rounded disabled:opacity-30 hover:bg-slate-800 transition-colors"
              title="Previous page"
            ><ChevronLeft className="w-3.5 h-3.5" /></button>

            {/* Page number buttons (window of 5) */}
            {Array.from({ length: totalPages }, (_, i) => i + 1)
              .filter((p) => Math.abs(p - safePage) <= 2)
              .map((p) => (
                <button
                  key={p}
                  onClick={() => goPage(p)}
                  className={`w-7 h-7 rounded font-semibold transition-colors ${
                    p === safePage
                      ? 'bg-emerald-500 text-slate-950'
                      : 'hover:bg-slate-800 text-slate-400'
                  }`}
                >{p}</button>
              ))}

            <button
              onClick={() => goPage(safePage + 1)}
              disabled={safePage === totalPages}
              className="p-1 rounded disabled:opacity-30 hover:bg-slate-800 transition-colors"
              title="Next page"
            ><ChevronRight className="w-3.5 h-3.5" /></button>
            <button
              onClick={() => goPage(totalPages)}
              disabled={safePage === totalPages}
              className="px-2 py-1 rounded font-bold disabled:opacity-30 hover:bg-slate-800 transition-colors"
              title="Last page"
            >»</button>
          </div>
        </div>
      )}
    </div>
  );
}
