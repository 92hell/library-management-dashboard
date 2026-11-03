import { render, screen } from '@testing-library/react';
import { StrictMode } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { vi } from 'vitest';
import { AppRoutes } from '../App';
import { AuthProvider } from '../auth';

export function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function problem(status, detail, errors) {
  return jsonResponse({ type: 'about:blank', title: 'Error', status, detail, errors }, status);
}

/**
 * Stubs global fetch with a router keyed by "METHOD /path" (query string ignored).
 * Unmatched requests answer 404 so missing stubs fail loudly.
 */
export function mockFetch(routes) {
  const fetchMock = vi.fn(async (url, init = {}) => {
    const path = String(url).split('?')[0];
    const handler = routes[`${init.method ?? 'GET'} ${path}`];
    if (!handler) return problem(404, `No stub for ${init.method ?? 'GET'} ${path}`);
    return typeof handler === 'function' ? handler(url, init) : jsonResponse(handler);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

/** Shows the router's current address (path + query) for currentLocation(). */
function LocationProbe() {
  const { pathname, search } = useLocation();
  return (
    <output data-testid="location" hidden>
      {pathname + search}
    </output>
  );
}

/**
 * Renders the full app at `path`. `strict` wraps it in React.StrictMode (effects run twice on mount);
 * `withLocation` adds a probe so currentLocation() can read the address.
 */
export function renderApp(path, { strict = false, withLocation = false } = {}) {
  const app = (
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
        {withLocation && <LocationProbe />}
      </MemoryRouter>
    </AuthProvider>
  );
  return render(strict ? <StrictMode>{app}</StrictMode> : app);
}

/** The address the app is at (needs renderApp(path, { withLocation: true })). */
export function currentLocation() {
  return screen.getByTestId('location').textContent;
}

export const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

export function page(content) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

export const librarian = { id: 1, username: 'lina', displayName: 'Lina', role: 'LIBRARIAN', memberId: null };
export const customer = { id: 2, username: 'siti', displayName: 'Siti', role: 'CUSTOMER', memberId: 7 };

/** Stubs shared by every signed-in page: the session, the navbar's unread count and the dashboard. */
export function signedInAs(user, routes = {}) {
  return mockFetch({
    'GET /api/auth/me': user,
    'GET /api/notifications/unread-count': { count: 0 },
    'GET /api/dashboard': { pendingRequests: 0, activeLoans: 0, overdueLoans: 0, dueToday: 0, books: 0, members: 0 },
    'GET /api/my/loans': [],
    'GET /api/my/fines': { balance: 0, currency: 'IDR', blocked: false, blockAt: 20000, fines: [] },
    'GET /api/auth/demo': { enabled: false, registrationOpen: true, accounts: [] },
    ...routes,
  });
}

/** The parsed JSON body of the first call to `METHOD path`, or undefined. */
export function sentBody(fetchMock, method, path) {
  const call = fetchMock.mock.calls.find(([url, init = {}]) => String(url).split('?')[0] === path && (init.method ?? 'GET') === method);
  return call && call[1].body ? JSON.parse(call[1].body) : call ? null : undefined;
}

export function loan(overrides = {}) {
  return {
    id: 11,
    book: { id: 3, title: 'Bumi Manusia', authorName: 'Pramoedya' },
    member: { id: 7, name: 'Siti' },
    status: 'ACTIVE',
    requestedAt: null,
    borrowDate: '2026-11-01',
    dueDate: '2026-11-15',
    returnDate: null,
    overdue: false,
    decisionNote: null,
    ...overrides,
  };
}
