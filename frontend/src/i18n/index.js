import { useCallback, useSyncExternalStore } from 'react';
import ID from './id';

/**
 * English / Bahasa Indonesia. The English text is the key: `t('My loans')` shows "Pinjaman saya" in
 * Indonesian and the text itself in English (or when a translation is missing). `{name}` placeholders
 * are filled from `vars`. Translations live in i18n/id/*.js; i18n.test.js fails the build when a
 * t('...') text has no Indonesian translation, or a translation is no longer used.
 */
export const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'id', label: 'Bahasa Indonesia' },
];
const STORAGE_KEY = 'library.lang';

function initialLanguage() {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (saved === 'en' || saved === 'id') return saved;
  } catch {
    // Storage blocked (private mode): fall back to the browser language.
  }
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('id') ? 'id' : 'en';
}

let current = initialLanguage();
const listeners = new Set();
applyToDocument(current);

function applyToDocument(lang) {
  if (typeof document !== 'undefined') document.documentElement.lang = lang;
}

export function getLanguage() {
  return current;
}

export function setLanguage(lang) {
  if (lang === current) return;
  current = lang;
  try {
    window.localStorage.setItem(STORAGE_KEY, lang);
  } catch {
    // Not persisted; the choice still applies until the page is reloaded.
  }
  applyToDocument(lang);
  listeners.forEach((listener) => listener());
}

function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Marks a text for translation where it is defined (e.g. a module-level list of labels) and returns
 * it unchanged; translate it where it is shown with t(label).
 */
export const msg = (text) => text;

/** Translates outside React (prefer useT in components so they re-render on a language change). */
export function translate(text, vars, lang = current) {
  const template = (lang === 'id' && ID[text]) || text;
  return vars ? template.replace(/\{(\w+)\}/g, (match, name) => (name in vars ? String(vars[name]) : match)) : template;
}

/** The current language code, re-rendering when it changes. */
export function useLanguage() {
  return useSyncExternalStore(subscribe, getLanguage, getLanguage);
}

/** `t(text, vars)` for the current language. */
export function useT() {
  const lang = useLanguage();
  return useCallback((text, vars) => translate(text, vars, lang), [lang]);
}

/**
 * Locale for Intl formatting: Indonesian dates and numbers in Indonesian, US English in English
 * ("Nov 6, 2026", "1,234") - not the browser's or operating system's own locale.
 */
export function locale(lang = current) {
  return lang === 'id' ? 'id-ID' : 'en-US';
}
