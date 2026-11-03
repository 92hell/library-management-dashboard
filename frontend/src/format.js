import { getLanguage, locale } from './i18n';

const dateFormats = new Map();

/** Formats a "YYYY-MM-DD" string in the current language, without shifting it across time zones. */
export function formatDate(isoDate, fallback = '—') {
  if (!isoDate) return fallback;
  const lang = getLanguage();
  if (!dateFormats.has(lang)) {
    dateFormats.set(lang, new Intl.DateTimeFormat(locale(lang), { year: 'numeric', month: 'short', day: 'numeric' }));
  }
  const [year, month, day] = isoDate.split('-').map(Number);
  return dateFormats.get(lang).format(new Date(year, month - 1, day));
}

/** Today's local date as "YYYY-MM-DD". */
export function todayIso() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** Empty form strings become null so optional values are sent as "not set". */
export function emptyToNull(value) {
  return value === '' ? null : value;
}

/** "IDR 25,000" ("IDR 25.000" in Indonesian): amounts are whole units of the currency. */
export function formatMoney(amount, currency) {
  return `${currency} ${Number(amount).toLocaleString(getLanguage() === 'id' ? 'id-ID' : 'en')}`;
}
