import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import ID from './id';
import { setLanguage, translate } from '.';

const SRC = join(import.meta.dirname, '..');

function sourceFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === 'test' || name === 'i18n' ? [] : sourceFiles(path);
    return /\.jsx?$/.test(name) && !/\.test\.jsx?$/.test(name) ? [path] : [];
  });
}

/** Every text passed to t('...'), translate('...') or msg('...') in the app's source. */
function usedTexts() {
  const texts = new Set();
  for (const file of sourceFiles(SRC)) {
    const code = readFileSync(file, 'utf8');
    for (const m of code.matchAll(/\b(?:t|translate|msg)\(\s*'((?:[^'\\]|\\.)*)'/g)) texts.add(m[1].replace(/\\'/g, "'"));
  }
  return texts;
}

const placeholders = (s) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();

describe('Translations (English / Bahasa Indonesia)', () => {
  const used = usedTexts();

  it('has an Indonesian translation for every text the app shows', () => {
    expect([...used].filter((text) => !(text in ID))).toEqual([]);
  });

  it('has no leftover translations for texts the app no longer shows', () => {
    expect(Object.keys(ID).filter((text) => !used.has(text))).toEqual([]);
  });

  it('keeps the same {placeholders} in both languages', () => {
    expect(Object.entries(ID).filter(([en, id]) => placeholders(en).join() !== placeholders(id).join())).toEqual([]);
  });

  it('fills placeholders and falls back to English', () => {
    expect(translate('Notifications ({count} unread)', { count: 3 }, 'id')).toBe('Notifikasi (3 belum dibaca)');
    expect(translate('Notifications ({count} unread)', { count: 3 }, 'en')).toBe('Notifications (3 unread)');
    expect(translate('Not translated', undefined, 'id')).toBe('Not translated');
  });

  it('marks the page with the chosen language', () => {
    setLanguage('id');
    expect(document.documentElement.lang).toBe('id');
    setLanguage('en');
    expect(document.documentElement.lang).toBe('en');
  });
});
