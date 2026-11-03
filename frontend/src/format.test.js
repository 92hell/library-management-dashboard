import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, formatNumber, localIsoDate } from './format';
import { locale, setLanguage } from './i18n';

describe('Formatting', () => {
  it('uses US English or Indonesian conventions, never the browser\'s own locale', () => {
    expect(locale('en')).toBe('en-US');
    expect(locale('id')).toBe('id-ID');
    expect(formatDate('2026-11-06')).toBe('Nov 6, 2026');
    setLanguage('id');
    expect(formatDate('2026-11-06')).toBe('6 Nov 2026');
  });

  it('shows rupiah as "Rp25.000" in Indonesian and "IDR 25,000" in English', () => {
    expect(formatMoney(25000, 'IDR')).toBe('IDR 25,000');
    expect(formatMoney(1234, 'EUR')).toBe('EUR 1,234');
    setLanguage('id');
    expect(formatMoney(25000, 'IDR')).toBe('Rp25.000');
    expect(formatMoney(1234, 'EUR')).toBe('EUR 1.234');
  });

  it('formats counts for the language', () => {
    expect(formatNumber(12345)).toBe('12,345');
    setLanguage('id');
    expect(formatNumber(12345)).toBe('12.345');
  });

  it('gives the local date of an instant', () => {
    const instant = new Date(2026, 10, 3, 1, 30).toISOString();
    expect(localIsoDate(instant)).toBe('2026-11-03');
  });
});
