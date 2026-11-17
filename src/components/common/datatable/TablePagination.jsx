import { ChevronLeft, ChevronRight } from 'lucide-react';
import { strings } from '../../../lib/strings';

function getPaginationItems(current, total) {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }
  if (current <= 4) {
    return [1, 2, 3, 4, 5, '...', total];
  }
  if (current >= total - 3) {
    return [1, '...', total - 4, total - 3, total - 2, total - 1, total];
  }
  return [1, '...', current - 1, current, current + 1, '...', total];
}

export default function TablePagination({
  currentPage,
  safePage = currentPage ?? 1,
  totalPages = 1,
  startRecord = 0,
  endRecord = 0,
  totalRecords = 0,
  onPageChange,
}) {
  const safeTotalPages = Math.max(1, totalPages || 1);
  const activePage = Math.max(1, Math.min(safePage, safeTotalPages));
  const hasMultiplePages = safeTotalPages > 1;
  const canGoPrev = hasMultiplePages && activePage > 1;
  const canGoNext = hasMultiplePages && activePage < safeTotalPages;

  return (
    <div className="flex items-center justify-between px-4 py-2 border-t border-border-default bg-surface shrink-0 text-xs text-text-secondary select-none">
      {/* Left: Current Page info */}
      <div className="flex-1 min-w-0 flex items-center">
        <span>
          {strings.common.table.page}{' '}
          <strong className="text-text-primary font-semibold">{activePage}</strong>{' '}
          {strings.common.table.of}{' '}
          <strong className="text-text-primary font-semibold">{safeTotalPages}</strong>
        </span>
      </div>

      {/* Center: Truncated Page navigation */}
      <div className="flex items-center justify-center gap-1 shrink-0">
        <button
          type="button"
          onClick={() => onPageChange(1)}
          disabled={!canGoPrev}
          aria-disabled={!canGoPrev}
          className="px-2 py-1 rounded font-bold transition-colors select-none disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-text-secondary hover:bg-surface-secondary text-text-secondary hover:text-text-primary cursor-pointer"
          title={strings.common.table.firstPage}
        >
          «
        </button>
        <button
          type="button"
          onClick={() => onPageChange(activePage - 1)}
          disabled={!canGoPrev}
          aria-disabled={!canGoPrev}
          className="p-1 rounded transition-colors select-none disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-text-secondary hover:bg-surface-secondary text-text-secondary hover:text-text-primary cursor-pointer"
          title={strings.common.table.previousPage}
        >
          <ChevronLeft className="w-3.5 h-3.5" />
        </button>

        {getPaginationItems(activePage, safeTotalPages).map((p, idx) =>
          p === '...' ? (
            <span
              key={`ellipsis-${idx}`}
              className="w-7 h-7 flex items-center justify-center text-text-muted font-bold select-none"
            >
              {strings.common.table.ellipsis}
            </span>
          ) : (
            <button
              key={p}
              type="button"
              onClick={() => onPageChange(p)}
              disabled={p === activePage}
              aria-current={p === activePage ? 'page' : undefined}
              className={`w-7 h-7 rounded font-semibold text-xs transition-colors select-none ${
                p === activePage
                  ? 'bg-brand text-white shadow-xs cursor-default'
                  : 'hover:bg-surface-secondary text-text-secondary hover:text-text-primary cursor-pointer'
              }`}
            >
              {p}
            </button>
          )
        )}

        <button
          type="button"
          onClick={() => onPageChange(activePage + 1)}
          disabled={!canGoNext}
          aria-disabled={!canGoNext}
          className="p-1 rounded transition-colors select-none disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-text-secondary hover:bg-surface-secondary text-text-secondary hover:text-text-primary cursor-pointer"
          title={strings.common.table.nextPage}
        >
          <ChevronRight className="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          onClick={() => onPageChange(safeTotalPages)}
          disabled={!canGoNext}
          aria-disabled={!canGoNext}
          className="px-2 py-1 rounded font-bold transition-colors select-none disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-text-secondary hover:bg-surface-secondary text-text-secondary hover:text-text-primary cursor-pointer"
          title={strings.common.table.lastPage}
        >
          »
        </button>
      </div>

      {/* Right: Record range */}
      <div className="flex-1 min-w-0 flex items-center justify-end text-right">
        <span>
          {strings.common.table.from}{' '}
          <strong className="text-text-primary font-semibold">{startRecord}</strong>{' '}
          {strings.common.table.to}{' '}
          <strong className="text-text-primary font-semibold">{endRecord}</strong>
          {totalRecords > 0 && (
            <span className="text-text-muted">{strings.common.table.rangeOf(totalRecords)}</span>
          )}
        </span>
      </div>
    </div>
  );
}
