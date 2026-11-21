// ── Locale state (synced from General Settings) ─────────────────────────────
// Keeping the locale in module scope lets plain helper functions stay simple
// while every view picks up the same currency symbol / date / time format.

const DEFAULT_LOCALE = {
  currencySymbol: '₹',
  currencyCode: 'INR',
  dateFormat: 'DD-MM-YYYY',
  timeFormat: '12h',
};

const MONTHS_SHORT = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

let localeSettings = { ...DEFAULT_LOCALE };

/**
 * Helper to parse various date formats safely without UTC date shift on date-only strings.
 */
function parseDateParts(input) {
  if (!input) return null;
  if (input instanceof Date) {
    return isNaN(input.getTime()) ? null : { date: input, hasTime: true };
  }
  if (typeof input === 'number') {
    const d = new Date(input);
    return isNaN(d.getTime()) ? null : { date: d, hasTime: true };
  }
  if (typeof input === 'string') {
    const trimmed = input.trim();
    if (!trimmed) return null;

    // YYYY-MM (e.g. from monthly reports breakdown)
    const yearMonthMatch = trimmed.match(/^(\d{4})-(\d{2})$/);
    if (yearMonthMatch) {
      return {
        date: new Date(Number(yearMonthMatch[1]), Number(yearMonthMatch[2]) - 1, 1),
        hasTime: false,
        isYearMonth: true,
      };
    }

    // YYYY-MM-DD (pure date string)
    const dateMatch = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (dateMatch) {
      return {
        date: new Date(Number(dateMatch[1]), Number(dateMatch[2]) - 1, Number(dateMatch[3])),
        hasTime: false,
      };
    }

    // SQLite space-separated timestamp: YYYY-MM-DD HH:MM:SS
    const sqliteIso = trimmed.replace(' ', 'T');
    const d = new Date(sqliteIso);
    if (!isNaN(d.getTime())) {
      return { date: d, hasTime: true };
    }

    const fallback = new Date(trimmed);
    if (!isNaN(fallback.getTime())) {
      return { date: fallback, hasTime: true };
    }
  }
  return null;
}

/**
 * Apply the persisted General Settings to all formatters.
 * Safe to call with partial / legacy settings objects.
 */
export function applyGeneralSettings(settings) {
  if (!settings) return;
  const currencySymbol = settings.currencySymbol ?? settings.currency_symbol;
  const currencyCode = settings.currencyCode ?? settings.currency_code;
  const dateFormat = settings.dateFormat ?? settings.date_format;
  const timeFormat = settings.timeFormat ?? settings.time_format;

  localeSettings = {
    currencySymbol:
      typeof currencySymbol === 'string' && currencySymbol.trim()
        ? currencySymbol.trim()
        : localeSettings.currencySymbol,
    currencyCode:
      typeof currencyCode === 'string' && currencyCode.trim()
        ? currencyCode.trim()
        : localeSettings.currencyCode,
    dateFormat:
      typeof dateFormat === 'string' && dateFormat.trim()
        ? dateFormat.trim()
        : localeSettings.dateFormat,
    timeFormat:
      typeof timeFormat === 'string' && timeFormat.trim()
        ? timeFormat.trim()
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
  if (paise === null || paise === undefined || isNaN(paise)) {
    return `${symbol}0.00`;
  }
  const isNegative = paise < 0;
  const absPaise = Math.abs(paise);
  const rupees = (absPaise / 100).toFixed(2);
  const sym = symbol || '';
  const sep = sym.length > 1 && !/^[^\w\s]+$/.test(sym) ? ' ' : '';
  return isNegative ? `-${sym}${sep}${rupees}` : `${sym}${sep}${rupees}`;
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

export function formatDate(dateInput) {
  if (!dateInput) return '';
  const parsed = parseDateParts(dateInput);
  if (!parsed) return String(dateInput);

  const { date: d, isYearMonth } = parsed;
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = d.getFullYear();

  const rawFormat = localeSettings.dateFormat || 'DD-MM-YYYY';
  const pattern = rawFormat.trim().toUpperCase();
  const separator = rawFormat.includes('/') ? '/' : rawFormat.includes('.') ? '.' : '-';

  if (isYearMonth) {
    if (pattern.includes('MONTH')) {
      return `${MONTHS_SHORT[d.getMonth()]} ${year}`;
    }
    return `${month}${separator}${year}`;
  }

  const norm = pattern.replace(/[\/\.]/g, '-');
  switch (norm) {
    case 'DD-MM-YYYY':
      return `${day}${separator}${month}${separator}${year}`;
    case 'MM-DD-YYYY':
      return `${month}${separator}${day}${separator}${year}`;
    case 'YYYY-MM-DD':
      return `${year}${separator}${month}${separator}${day}`;
    case 'MONTH DATE, YEAR':
      return `${MONTHS_SHORT[d.getMonth()]} ${day}, ${year}`;
    case 'DATE MONTH, YEAR':
      return `${day} ${MONTHS_SHORT[d.getMonth()]}, ${year}`;
    default:
      return `${day}${separator}${month}${separator}${year}`;
  }
}

export function formatTime(dateInput) {
  if (!dateInput) return '';
  const parsed = parseDateParts(dateInput);
  if (!parsed || !parsed.hasTime) return '';

  try {
    const is24Hour = (localeSettings.timeFormat || '').includes('24');
    return parsed.date.toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      hour12: !is24Hour,
    });
  } catch {
    return '';
  }
}

export function formatDateTime(dateInput) {
  if (!dateInput) return '';
  const dStr = formatDate(dateInput);
  const tStr = formatTime(dateInput);
  return tStr ? `${dStr} ${tStr}` : dStr;
}
