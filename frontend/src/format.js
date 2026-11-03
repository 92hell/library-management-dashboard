const DATE_FORMAT = new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' });

/** Formats a "YYYY-MM-DD" string for display without shifting it across time zones. */
export function formatDate(isoDate, fallback = '—') {
  if (!isoDate) return fallback;
  const [year, month, day] = isoDate.split('-').map(Number);
  return DATE_FORMAT.format(new Date(year, month - 1, day));
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
