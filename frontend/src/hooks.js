import { useCallback, useEffect, useState } from 'react';
import { api, buildUrl } from './api';
import { useLanguage } from './i18n';

export const PAGE_SIZE = 20;

/**
 * Loads `path` (with optional query params) and reloads whenever the URL changes.
 * Pass a null path to skip loading. While a new URL loads, the previous data stays
 * available so tables don't flash empty between pages.
 */
export function useApi(path, params) {
  // Server text comes in the chosen language, so a language switch reloads the data too.
  const lang = useLanguage();
  const url = path ? buildUrl(path, params) : null;
  const [reloadCount, setReloadCount] = useState(0);
  const [result, setResult] = useState({ key: null, data: undefined, error: null });

  useEffect(() => {
    if (!url) return undefined;
    const key = `${url}#${reloadCount}#${lang}`;
    let ignore = false;
    api.get(url).then(
      (data) => !ignore && setResult({ key, data, error: null }),
      (error) => !ignore && setResult({ key, data: undefined, error }),
    );
    return () => {
      ignore = true;
    };
  }, [url, reloadCount, lang]);

  const reload = useCallback(() => setReloadCount((count) => count + 1), []);

  if (!url) return { data: undefined, error: null, loading: false, reload };
  const isCurrent = result.key === `${url}#${reloadCount}#${lang}`;
  return {
    data: result.data,
    error: isCurrent ? result.error : null,
    loading: !isCurrent,
    reload,
  };
}

/**
 * A paginated list endpoint. Changing any filter jumps back to the first page.
 */
export function usePagedList(path, filters) {
  const filterKey = JSON.stringify(filters);
  const [paging, setPaging] = useState({ filterKey, page: 0 });
  const page = paging.filterKey === filterKey ? paging.page : 0;
  const result = useApi(path, { ...filters, page, size: PAGE_SIZE });

  const setPage = useCallback((nextPage) => setPaging({ filterKey, page: nextPage }), [filterKey]);

  const { reload } = result;
  const isLastRowOnPage = result.data?.content.length === 1;
  /** Refreshes after a row was removed, stepping back a page if it was the last row on it. */
  const refreshAfterRemoval = useCallback(() => {
    if (isLastRowOnPage && page > 0) setPage(page - 1);
    else reload();
  }, [isLastRowOnPage, page, setPage, reload]);

  return { ...result, page, setPage, refreshAfterRemoval };
}

export function useDebouncedValue(value, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

/** Form state with a single change handler for text inputs, selects and checkboxes. */
export function useFormFields(initialValues) {
  const [values, setValues] = useState(initialValues);
  const handleChange = useCallback((event) => {
    const { name, type, value, checked } = event.target;
    setValues((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
  }, []);
  return [values, setValues, handleChange];
}

/**
 * Runs an API call and tracks its pending state and error. Used for form submits and
 * list actions alike: `fieldErrors` holds either client-side validation messages or the
 * server's per-field errors, `error` the ApiError to show in an alert.
 */
export function useAction() {
  const [state, setState] = useState({ pending: false, error: null, fieldErrors: {} });

  const run = useCallback(async (action, clientErrors = {}) => {
    if (Object.keys(clientErrors).length > 0) {
      setState({ pending: false, error: null, fieldErrors: clientErrors });
      return false;
    }
    setState({ pending: true, error: null, fieldErrors: {} });
    try {
      await action();
      setState({ pending: false, error: null, fieldErrors: {} });
      return true;
    } catch (error) {
      setState({ pending: false, error, fieldErrors: error.fieldErrors ?? {} });
      return false;
    }
  }, []);

  const clearError = useCallback(() => setState((current) => ({ ...current, error: null })), []);

  return { ...state, run, clearError };
}
