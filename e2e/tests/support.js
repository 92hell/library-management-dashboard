import { request as playwrightRequest, expect } from '@playwright/test';
import { LIBRARIAN } from '../playwright.config.js';

export { LIBRARIAN };
export const PASSWORD = 'a-long-e2e-password';

/** Short unique suffix so every test works on its own data. */
export function unique() {
  return `${Date.now().toString(36)}${Math.floor(Math.random() * 1e4).toString(36)}`;
}

export function isoDaysFromToday(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * An API client signed in as `username`, following the app's CSRF scheme (XSRF-TOKEN cookie
 * echoed in the X-XSRF-TOKEN header), for setting up data quickly.
 */
export async function apiAs(baseURL, username, password) {
  const api = await playwrightRequest.newContext({ baseURL });
  const call = async (method, url, data) => {
    await api.get('/api/auth/me');
    const token = (await api.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
    const response = await api.fetch(url, { method, data, headers: token ? { 'X-XSRF-TOKEN': token } : {} });
    expect(response.ok(), `${method} ${url}: ${response.status()} ${await response.text()}`).toBeTruthy();
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  };
  await call('POST', '/api/auth/login', { username, password });
  return {
    get: (url) => call('GET', url),
    post: (url, data) => call('POST', url, data),
    dispose: () => api.dispose(),
  };
}

/** A book by a new author, with `copies` copies; returns the created book. */
export async function createBook(librarian, title, { copies = 1, categories = ['Fiction'], author } = {}) {
  const a = author ?? (await librarian.post('/api/authors', { name: `Author ${unique()}` }));
  return librarian.post('/api/books', { title, publishedDate: '2005-01-01', authorId: a.id, categories, copies });
}

/** Registers a customer online; returns { username, memberId }. */
export async function registerCustomer(baseURL, name = 'Rina Wijaya') {
  const username = `rina-${unique()}`;
  const api = await playwrightRequest.newContext({ baseURL });
  await api.get('/api/auth/me');
  const token = (await api.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  const response = await api.post('/api/auth/register', {
    headers: { 'X-XSRF-TOKEN': token },
    data: { username, password: PASSWORD, name, email: `${username}@example.com`, phone: '081234567890', dateOfBirth: '1995-03-04' },
  });
  expect(response.status(), await response.text()).toBe(201);
  const body = await response.json();
  await api.dispose();
  return { username, memberId: body.memberId };
}

/** Signs in through the login form. */
export async function signIn(page, username, password) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: /Welcome/ })).toBeVisible();
}
