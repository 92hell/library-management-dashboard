import { getLanguage, locale } from './i18n';

const dateFormats = new Map();

/**
 * Formats a "YYYY-MM-DD" string in the current language, without shifting it across time zones
 * (the date is formatted as a UTC date, whatever the reader's time zone).
 */
export function formatDate(isoDate, fallback = '—') {
  if (!isoDate) return fallback;
  const lang = getLanguage();
  if (!dateFormats.has(lang)) {
    dateFormats.set(lang, new Intl.DateTimeFormat(locale(lang), { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' }));
  }
  const [year, month, day] = isoDate.split('-').map(Number);
  return dateFormats.get(lang).format(new Date(Date.UTC(year, month - 1, day)));
}

/** The reader's local date of an instant (e.g. "2026-11-02T18:30:00Z" is "2026-11-03" in Jakarta). */
export function localIsoDate(instant) {
  return isoDateOf(new Date(instant));
}

function isoDateOf(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Today's local date as "YYYY-MM-DD". */
export function todayIso() {
  return isoDateOf(new Date());
}

/** Empty form strings become null so optional values are sent as "not set". */
export function emptyToNull(value) {
  return value === '' ? null : value;
}

/** A count in the current language: "1,234" in English, "1.234" in Indonesian. */
export function formatNumber(value) {
  return Number(value).toLocaleString(locale());
}

/**
 * Amounts are whole units of the currency. English: "IDR 25,000". Indonesian writes rupiah the
 * Indonesian way, "Rp25.000", and other currencies as "EUR 1.234".
 */
export function formatMoney(amount, currency) {
  const number = formatNumber(amount);
  return getLanguage() === 'id' && currency === 'IDR' ? `Rp${number}` : `${currency} ${number}`;
}
