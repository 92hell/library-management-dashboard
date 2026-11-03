import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, buildUrl, onUnauthorized } from './api';
import { jsonResponse, mockFetch, problem } from './test/utils';

describe('api', () => {
  let unsubscribe = () => {};
  afterEach(() => unsubscribe());

  it('builds query strings from non-empty params', () => {
    expect(buildUrl('/api/books', { q: 'dune', page: 0, borrowDate: '', status: undefined })).toBe(
      '/api/books?q=dune&page=0',
    );
    expect(buildUrl('/api/books')).toBe('/api/books');
  });

  it('sends the decoded XSRF cookie on writes but not on reads', async () => {
    document.cookie = 'XSRF-TOKEN=abc%3D123';
    const fetchMock = mockFetch({
      'GET /api/authors/1': { id: 1, name: 'Ursula' },
      'POST /api/authors': () => jsonResponse({ id: 2, name: 'Iain' }, 201),
    });

    await api.get('/api/authors/1');
    const created = await api.post('/api/authors', { name: 'Iain' });

    const [, getInit] = fetchMock.mock.calls[0];
    const [, postInit] = fetchMock.mock.calls[1];
    expect(getInit.headers['X-XSRF-TOKEN']).toBeUndefined();
    expect(postInit.headers['X-XSRF-TOKEN']).toBe('abc=123');
    expect(postInit.headers['Content-Type']).toBe('application/json');
    expect(postInit.credentials).toBe('same-origin');
    expect(postInit.body).toBe(JSON.stringify({ name: 'Iain' }));
    expect(created).toEqual({ id: 2, name: 'Iain' });
  });

  it('returns null for 204 responses', async () => {
    mockFetch({ 'DELETE /api/books/1': () => new Response(null, { status: 204 }) });
    await expect(api.delete('/api/books/1')).resolves.toBeNull();
  });

  it('turns problem details into an ApiError with field errors', async () => {
    mockFetch({
      'POST /api/members': () => problem(400, 'Validation failed', { email: 'must be a well-formed email address' }),
    });

    const error = await api.post('/api/members', {}).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe('Validation failed');
    expect(error.fieldErrors).toEqual({ email: 'must be a well-formed email address' });
  });

  it('falls back to the problem title and to empty field errors', async () => {
    mockFetch({
      'DELETE /api/books/7': () => jsonResponse({ title: 'Conflict', status: 409 }, 409),
    });

    const error = await api.delete('/api/books/7').catch((e) => e);

    expect(error.message).toBe('Conflict');
    expect(error.fieldErrors).toEqual({});
  });

  it('reports 401s outside /api/auth to the unauthorized listener', async () => {
    const listener = vi.fn();
    unsubscribe = onUnauthorized(listener);
    mockFetch({
      'GET /api/books': () => problem(401, 'Full authentication is required'),
      'GET /api/auth/me': () => problem(401, 'Not signed in'),
    });

    await expect(api.get('/api/auth/me')).rejects.toMatchObject({ status: 401 });
    expect(listener).not.toHaveBeenCalled();

    await expect(api.get('/api/books')).rejects.toMatchObject({ status: 401 });
    expect(listener).toHaveBeenCalledOnce();
  });

  it('wraps network failures in an ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(api.get('/api/books')).rejects.toMatchObject({ name: 'ApiError', status: 0 });
  });
});
