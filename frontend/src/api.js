import { getLanguage } from './i18n';
/**
 * Thin fetch wrapper for the backend API.
 *
 * - Sends cookies (same-origin) and JSON in both directions.
 * - Echoes the XSRF-TOKEN cookie in the X-XSRF-TOKEN header on state-changing requests.
 * - Turns RFC 7807 problem responses into {@link ApiError}s.
 * - Reports 401s (outside /api/auth/*) so the app can send the user back to the login page.
 */
import { msg, translate } from './i18n';

const AUTH_PATH = '/api/auth/';

/**
 * A failed request. `message` is the server's message, or - when the client words it itself - the
 * English text; then `text` and `vars` hold the untranslated message so it can be shown in the reader's
 * language (see errorText in components/Feedback).
 */
export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}, vars = undefined) {
    super(vars ? translate(message, vars, 'en') : message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.text = message;
    this.vars = vars;
  }
}

let unauthorizedListener = null;

/** Registers the callback invoked when a request fails with 401. Returns an unsubscribe function. */
export function onUnauthorized(listener) {
  unauthorizedListener = listener;
  return () => {
    if (unauthorizedListener === listener) unauthorizedListener = null;
  };
}

/** Appends the non-empty params to the path as a query string. */
export function buildUrl(path, params = {}) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') query.append(key, value);
  }
  const queryString = query.toString();
  return queryString ? `${path}?${queryString}` : path;
}

function readCookie(name) {
  const prefix = `${name}=`;
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

async function toApiError(response) {
  let problem = {};
  try {
    problem = await response.json();
  } catch {
    // Not a JSON body (e.g. a proxy error page); fall back to the status text.
  }
  const message = problem.detail || problem.title || response.statusText;
  if (!message) return new ApiError(response.status, msg('Request failed with status {status}'), {}, { status: response.status });
  return new ApiError(response.status, message, problem.errors ?? {});
}

export async function request(path, { method = 'GET', params, body } = {}) {
  // The server writes its messages (errors, notifications) in the app's language.
  const headers = { Accept: 'application/json', 'Accept-Language': getLanguage() };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    const csrfToken = readCookie('XSRF-TOKEN');
    if (csrfToken) headers['X-XSRF-TOKEN'] = csrfToken;
  }

  let response;
  try {
    response = await fetch(buildUrl(path, params), {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, msg('Could not reach the server. Please check your connection and try again.'));
  }

  if (!response.ok) {
    if (response.status === 401 && !path.startsWith(AUTH_PATH)) unauthorizedListener?.();
    throw await toApiError(response);
  }
  if (response.status === 204) return null;

  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  get: (path, params) => request(path, { params }),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  delete: (path) => request(path, { method: 'DELETE' }),
};
