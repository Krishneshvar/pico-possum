import { ChevronsUpDown, ArrowUp, ArrowDown } from 'lucide-react';

/**
 * Sortable <th> for plain markup tables.
 * Cycles ascending → descending → unsorted, mirroring DataTable.
 */
export default function SortableTh({
  sortKey,
  sort,
  onSort,
  align = 'left',
  className = '',
  children,
  ...rest
}) {
  const isActive = sort?.key === sortKey && !!sort?.dir;
  const Icon = isActive ? (sort.dir === 'asc' ? ArrowUp : ArrowDown) : ChevronsUpDown;
  const alignClass = align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left';

  return (
    <th
      {...rest}
      onClick={() => onSort(sortKey)}
      aria-sort={isActive ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
      title={`Sort by this column`}
      className={`cursor-pointer select-none group/th ${alignClass} ${className}`}
    >
      <span className="inline-flex items-center gap-1">
        <span>{children}</span>
        <Icon
          className={`w-3 h-3 shrink-0 transition-opacity ${
            isActive ? 'text-emerald-400 opacity-100' : 'text-slate-500 opacity-0 group-hover/th:opacity-100'
          }`}
          strokeWidth={isActive ? 3 : 2}
        />
      </span>
    </th>
  );
}
