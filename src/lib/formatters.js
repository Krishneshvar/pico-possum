// ── Locale state (synced from General Settings) ─────────────────────────────
// Keeping the locale in module scope lets plain helper functions stay simple
// while every view picks up the same currency symbol / date / time format.

const DEFAULT_LOCALE = {
  currencySymbol: '₹',
  currencyCode: 'INR',
  dateFormat: 'DD/MM/YYYY',
  timeFormat: '12h',
};

const MONTHS_SHORT = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

let localeSettings = { ...DEFAULT_LOCALE };

/**
 * Apply the persisted General Settings to all formatters.
 * Safe to call with partial / legacy settings objects.
 */
export function applyGeneralSettings(settings) {
  if (!settings) return;
  localeSettings = {
    currencySymbol:
      typeof settings.currency_symbol === 'string' && settings.currency_symbol.trim()
        ? settings.currency_symbol.trim()
        : localeSettings.currencySymbol,
    currencyCode:
      typeof settings.currency_code === 'string' && settings.currency_code.trim()
        ? settings.currency_code.trim()
        : localeSettings.currencyCode,
    dateFormat:
      typeof settings.date_format === 'string' && settings.date_format.trim()
        ? settings.date_format.trim()
        : localeSettings.dateFormat,
    timeFormat:
      typeof settings.time_format === 'string' && settings.time_format.trim()
        ? settings.time_format.trim()
        : localeSettings.timeFormat,
  };
}

export function getLocaleSettings() {
  return { ...localeSettings };
}

export function getCurrencySymbol() {
  return localeSettings.currencySymbol;
}

// ── Currency ────────────────────────────────────────────────────────────────

export function formatPaise(paise, symbol = localeSettings.currencySymbol) {
  if (paise === null || paise === undefined || isNaN(paise)) return `${symbol}0.00`;
  const isNegative = paise < 0;
  const absPaise = Math.abs(paise);
  const rupees = (absPaise / 100).toFixed(2);
  return isNegative ? `-${symbol}${rupees}` : `${symbol}${rupees}`;
}

export function paiseToRupees(paise) {
  if (!paise || isNaN(paise)) return 0;
  return Number((paise / 100).toFixed(2));
}

export function rupeesToPaise(rupees) {
  if (!rupees || isNaN(rupees)) return 0;
  return Math.round(Number(rupees) * 100);
}

// ── Date & Time ─────────────────────────────────────────────────────────────

export function formatDate(dateStr) {
  if (!dateStr) return '';
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;

    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();

    const rawFormat = localeSettings.dateFormat;
    const pattern = rawFormat.trim().toUpperCase();
    const separator = rawFormat.includes('/') ? '/' : '-';

    switch (pattern) {
      case 'DD/MM/YYYY':
      case 'DD-MM-YYYY':
        return `${day}${separator}${month}${separator}${year}`;
      case 'MM/DD/YYYY':
      case 'MM-DD-YYYY':
        return `${month}${separator}${day}${separator}${year}`;
      case 'YYYY/MM/DD':
      case 'YYYY-MM-DD':
        return `${year}${separator}${month}${separator}${day}`;
      case 'MONTH DATE, YEAR':
        return `${MONTHS_SHORT[d.getMonth()]} ${day}, ${year}`;
      case 'DATE MONTH, YEAR':
        return `${day} ${MONTHS_SHORT[d.getMonth()]}, ${year}`;
      default:
        return `${day}/${month}/${year}`;
    }
  } catch {
    return dateStr;
  }
}

export function formatTime(dateStr) {
  if (!dateStr) return '';
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '';
    const is24Hour = localeSettings.timeFormat.includes('24');
    return d.toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      hour12: !is24Hour,
    });
  } catch {
    return '';
  }
}

export function formatDateTime(dateStr) {
  if (!dateStr) return '';
  return `${formatDate(dateStr)} ${formatTime(dateStr)}`;
}
