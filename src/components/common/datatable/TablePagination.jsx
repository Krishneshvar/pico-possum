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
  safePage,
  totalPages,
  startRecord,
  endRecord,
  totalRecords,
  onPageChange,
}) {
  return (
    <div className="flex items-center justify-between px-4 py-2 border-t border-border-default bg-surface shrink-0 text-xs text-text-secondary select-none">
      {/* Left: Current Page info */}
      <div className="flex-1 min-w-0 flex items-center">
        <span>
          Page <strong className="text-text-primary font-semibold">{safePage}</strong> {strings.common.table.of}{' '}
          <strong className="text-text-primary font-semibold">{totalPages}</strong>
        </span>
      </div>

      {/* Center: Truncated Page navigation */}
      <div className="flex items-center justify-center gap-1 shrink-0">
        <button
          type="button"
          onClick={() => onPageChange(1)}
          disabled={safePage === 1}
          className="px-2 py-1 rounded font-bold disabled:opacity-30 hover:bg-surface text-text-secondary hover:text-text-primary transition-colors cursor-pointer disabled:cursor-not-allowed"
          title={strings.common.table.firstPage}
        >
          «
        </button>
        <button
          type="button"
          onClick={() => onPageChange(safePage - 1)}
          disabled={safePage === 1}
          className="p-1 rounded disabled:opacity-30 hover:bg-surface text-text-secondary hover:text-text-primary transition-colors cursor-pointer disabled:cursor-not-allowed"
          title={strings.common.table.previousPage}
        >
          <ChevronLeft className="w-3.5 h-3.5" />
        </button>

        {getPaginationItems(safePage, totalPages).map((p, idx) =>
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
              className={`w-7 h-7 rounded font-semibold text-xs transition-colors cursor-pointer ${
                p === safePage
                  ? 'bg-brand text-white shadow-xs'
                  : 'hover:bg-surface text-text-secondary hover:text-text-primary'
              }`}
            >
              {p}
            </button>
          )
        )}

        <button
          type="button"
          onClick={() => onPageChange(safePage + 1)}
          disabled={safePage === totalPages}
          className="p-1 rounded disabled:opacity-30 hover:bg-surface text-text-secondary hover:text-text-primary transition-colors cursor-pointer disabled:cursor-not-allowed"
          title={strings.common.table.nextPage}
        >
          <ChevronRight className="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          onClick={() => onPageChange(totalPages)}
          disabled={safePage === totalPages}
          className="px-2 py-1 rounded font-bold disabled:opacity-30 hover:bg-surface text-text-secondary hover:text-text-primary transition-colors cursor-pointer disabled:cursor-not-allowed"
          title={strings.common.table.lastPage}
        >
          »
        </button>
      </div>

      {/* Right: Record range */}
      <div className="flex-1 min-w-0 flex items-center justify-end text-right">
        <span>
          {strings.common.table.from} <strong className="text-text-primary font-semibold">{startRecord}</strong> {strings.common.table.to}{' '}
          <strong className="text-text-primary font-semibold">{endRecord}</strong>
          {totalRecords > 0 && (
            <span className="text-text-muted">{strings.common.table.rangeOf(totalRecords)}</span>
          )}
        </span>
      </div>
    </div>
  );
}
