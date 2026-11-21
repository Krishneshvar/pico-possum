import { useState, useMemo, useEffect } from 'react';
import {
  ChevronsUpDown,
  ArrowUp,
  ArrowDown,
  GripVertical,
} from 'lucide-react';
import { nextSortState, sortRows } from '../../lib/sort';
import { strings } from '../../lib/strings';
import { getSortValue } from './datatable/tableState';
import TablePagination from './datatable/TablePagination';
import DataTableToolbar from './datatable/DataTableToolbar';
import { useTableColumns } from './datatable/useTableColumns';
import { useTableFiltering } from './datatable/useTableFiltering';

export default function DataTable({
  columns = [],
  data = [],
  keyFn,
  searchKeys,
  filters = [],
  pageSize = 20,
  emptyMessage = strings.common.states.noRecords,
  actions,
  isLoading = false,
  headerRight,
  onRowClick,
  onRowDoubleClick,
  persistKey,
  onFilteredChange,
  selectedRowId = null,
}) {
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState({ key: null, dir: null });

  const filterState = useTableFiltering({ data, filters, searchKeys, setPage });
  const columnState = useTableColumns({ columns, persistKey });

  // Reset page to 1 when data changes significantly
  useEffect(() => {
    setPage(1);
  }, [data.length]);

  // Notify parent of filtered data changes
  useEffect(() => {
    if (onFilteredChange) {
      onFilteredChange(filterState.filtered);
    }
  }, [filterState.filtered, onFilteredChange]);

  const toggleSort = (colKey) => {
    setSort((prev) => nextSortState(prev, colKey));
  };

  // Sort pipeline
  const sorted = useMemo(() => {
    if (!sort.key || !sort.dir) return filterState.filtered;
    const col = columns.find((c) => c.key === sort.key);
    return sortRows(filterState.filtered, sort, (row) => getSortValue(row, col));
  }, [filterState.filtered, sort, columns]);

  // Pagination
  const totalPages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const startIdx = (currentPage - 1) * pageSize;
  const startRecord = sorted.length === 0 ? 0 : startIdx + 1;
  const endRecord = Math.min(startIdx + pageSize, sorted.length);
  const pagedData = useMemo(() => {
    return sorted.slice(startIdx, startIdx + pageSize);
  }, [sorted, startIdx, pageSize]);

  return (
    <div className="flex-1 flex flex-col min-h-0 h-full bg-canvas">
      {/* ── Toolbar: Search & Filters ── */}
      <DataTableToolbar
        search={filterState.search}
        handleSearch={filterState.handleSearch}
        enrichedFilters={filterState.enrichedFilters}
        selectValues={filterState.selectValues}
        handleSelect={filterState.handleSelect}
        multiValues={filterState.multiValues}
        handleMulti={filterState.handleMulti}
        rangeValues={filterState.rangeValues}
        handleRangeMin={filterState.handleRangeMin}
        handleRangeMax={filterState.handleRangeMax}
        dateRangeKey={filterState.dateRangeKey}
        dateValues={filterState.dateValues}
        handleDateFrom={filterState.handleDateFrom}
        handleDateTo={filterState.handleDateTo}
        hasActiveFilters={filterState.hasActiveFilters}
        clearAllFilters={filterState.clearAllFilters}
        headerRight={headerRight}
        filtered={filterState.filtered}
      />

      {/* ── Table Container ── */}
      <div className="flex-1 min-h-0 px-4 py-3 flex flex-col">
        <div className="flex-1 overflow-x-auto overflow-y-auto rounded-xl border border-border-default bg-surface shadow-2xs">
          <table className="w-full text-left border-collapse select-text">
            <thead className="bg-table-header-bg text-[11px] font-semibold text-table-header-text uppercase tracking-wider sticky top-0 z-10 border-b border-table-header-border shadow-2xs">
              <tr className="h-10">
                {columnState.orderedColumns.map((col, colIdx) => {
                  const widthStyle = columnState.colWidths[col.key]
                    ? { width: `${columnState.colWidths[col.key]}px`, minWidth: `${columnState.colWidths[col.key]}px` }
                    : col.minWidth
                    ? { minWidth: `${col.minWidth}px` }
                    : {};

                  const isDropTarget = columnState.dragOverColKey === col.key && columnState.draggedColKey !== col.key;
                  const isDraggingThis = columnState.draggedColKey === col.key;
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
                      onDragStart={(e) => columnState.handleDragStart(e, col.key)}
                      onDragOver={(e) => columnState.handleDragOver(e, col.key)}
                      onDrop={(e) => columnState.handleDrop(e, col.key)}
                      onDragEnd={columnState.handleDragEnd}
                      onClick={() => {
                        if (!isSortable || columnState.suppressHeaderClick.current) return;
                        toggleSort(col.key);
                      }}
                      title={isSortable ? strings.common.table.sortBy(col.label) : undefined}
                      aria-sort={isSorted ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
                      style={widthStyle}
                      className={`relative py-2.5 px-3.5 ${colIdx === 0 ? 'pl-4' : ''} ${
                        !actions && colIdx === columnState.orderedColumns.length - 1 ? 'pr-4' : ''
                      } transition-colors select-none group/th ${
                        isSortable ? 'cursor-pointer hover:bg-white/10' : ''
                      } ${col.headerClassName ?? ''} ${isDraggingThis ? 'opacity-30' : ''} ${
                        isDropTarget ? 'bg-brand-subtle border-l-2 border-brand' : ''
                      }`}
                    >
                      <div className="flex items-center gap-1.5 overflow-hidden">
                        <GripVertical className="w-3 h-3 text-table-header-text/40 opacity-40 group-hover/th:opacity-100 transition-opacity shrink-0 cursor-grab active:cursor-grabbing" />
                        <span className="truncate">{col.label}</span>
                        {isSortable && (
                          <SortIcon
                            className={`w-3.5 h-3.5 shrink-0 transition-colors ${
                              isSorted ? 'text-table-header-text' : 'text-table-header-text/40 group-hover/th:text-table-header-text/70'
                            }`}
                          />
                        )}
                      </div>

                      {/* Resize Handle */}
                      <div
                        onMouseDown={(e) => columnState.startResizing(e, col.key)}
                        onClick={(e) => e.stopPropagation()}
                        title={strings.common.table.dragToResize}
                        className="absolute top-0 right-0 bottom-0 w-3 -mr-1.5 cursor-col-resize select-none flex items-center justify-center group/resize z-20"
                      >
                        <div className="w-[1.5px] h-3.5 bg-border-strong group-hover/resize:bg-brand group-hover/resize:w-[2px] group-hover/resize:h-full transition-all" />
                      </div>
                    </th>
                  );
                })}
                {actions && (
                  <th className="py-2.5 px-3.5 pr-4 text-right sticky right-0 z-10 w-24 bg-table-header-bg">
                    {strings.common.table.actions}
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-border-default text-xs">
              {isLoading ? (
                <tr>
                  <td
                    colSpan={columnState.orderedColumns.length + (actions ? 1 : 0)}
                    className="py-12 text-center text-text-muted"
                  >
                    <div className="flex items-center justify-center gap-2">
                      <div className="w-4 h-4 border-2 border-brand border-t-transparent rounded-full animate-spin" />
                      {strings.common.states.loading}
                    </div>
                  </td>
                </tr>
              ) : pagedData.length === 0 ? (
                <tr>
                  <td
                    colSpan={columnState.orderedColumns.length + (actions ? 1 : 0)}
                    className="py-12 text-center text-text-muted"
                  >
                    {emptyMessage}
                  </td>
                </tr>
              ) : (
                pagedData.map((row, rIdx) => {
                  const key = keyFn ? keyFn(row) : row.id ?? rIdx;
                  const isSelected = selectedRowId != null && key === selectedRowId;
                  const isInteractive = Boolean(onRowClick || onRowDoubleClick);

                  return (
                    <tr
                      key={key}
                      onClick={() => onRowClick && onRowClick(row)}
                      onDoubleClick={() => onRowDoubleClick && onRowDoubleClick(row)}
                      className={`transition-colors ${
                        isSelected
                          ? 'bg-brand/10 border-l-2 border-l-brand'
                          : rIdx % 2 === 1
                          ? 'bg-surface-secondary/40 hover:bg-surface-secondary'
                          : 'hover:bg-surface-secondary'
                      } ${isInteractive ? 'cursor-pointer' : ''}`}
                    >
                      {columnState.orderedColumns.map((col, cIdx) => (
                        <td
                          key={col.key}
                          className={`py-2 px-3.5 ${cIdx === 0 ? 'pl-4' : ''} ${
                            !actions && cIdx === columnState.orderedColumns.length - 1 ? 'pr-4' : ''
                          } text-text-primary ${col.className ?? ''}`}
                        >
                          {col.render ? col.render(row) : row[col.key]}
                        </td>
                      ))}
                      {actions && (
                        <td
                          className="py-2 px-3.5 pr-4 text-right sticky right-0 bg-surface/90 backdrop-blur-xs"
                          onClick={(e) => e.stopPropagation()}
                        >
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
      </div>

      {/* ── Standardized Pagination Bar (Fixed at the bottom of the page) ── */}
      <TablePagination
        currentPage={currentPage}
        safePage={currentPage}
        totalPages={totalPages}
        startRecord={startRecord}
        endRecord={endRecord}
        totalRecords={sorted.length}
        onPageChange={setPage}
      />
    </div>
  );
}
