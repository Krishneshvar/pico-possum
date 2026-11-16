export const TABLE_STATE_PREFIX = 'pico-possum:table-state:';

export function readTableState(key) {
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

export function writeTableState(key, state) {
  if (!key || typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(TABLE_STATE_PREFIX + key, JSON.stringify(state));
  } catch {
    // Best-effort storage
  }
}

export function getSortValue(row, col) {
  if (typeof col.sortValue === 'function') return col.sortValue(row);
  if (col.sortKey) return row[col.sortKey];
  return row[col.key];
}
