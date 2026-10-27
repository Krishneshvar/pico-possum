// Shared sorting helpers for tables.

export function isEmptySortValue(value) {
  return value === null || value === undefined || value === '';
}

export function compareSortValues(a, b) {
  // Compare as numbers whenever either side is numeric
  if (typeof a === 'number' || typeof b === 'number') {
    const numA = Number(a);
    const numB = Number(b);
    if (Number.isFinite(numA) && Number.isFinite(numB)) return numA - numB;
  }

  if (typeof a === 'boolean' || typeof b === 'boolean') {
    return (a === true ? 1 : 0) - (b === true ? 1 : 0);
  }

  // Natural, case-insensitive comparison (dates sort correctly as ISO strings)
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
}

/**
 * Toggle a column's sort state: none -> asc -> desc -> none.
 */
export function nextSortState(prev, key) {
  if (prev.key !== key || !prev.dir) return { key, dir: 'asc' };
  if (prev.dir === 'asc') return { key, dir: 'desc' };
  return { key: null, dir: null };
}

/**
 * Return a sorted copy of `rows` (empty values always last).
 * `getValue(row, key)` resolves the sortable value for a column.
 */
export function sortRows(rows, sort, getValue) {
  if (!sort?.key || !sort?.dir) return rows;

  const direction = sort.dir === 'asc' ? 1 : -1;

  return [...rows].sort((rowA, rowB) => {
    const a = getValue(rowA, sort.key);
    const b = getValue(rowB, sort.key);
    const aEmpty = isEmptySortValue(a);
    const bEmpty = isEmptySortValue(b);

    // Keep empty values at the bottom in both directions
    if (aEmpty && bEmpty) return 0;
    if (aEmpty) return 1;
    if (bEmpty) return -1;

    return direction * compareSortValues(a, b);
  });
}
