import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { currentLocation, customer, jsonResponse, mockFetch, page, problem, renderApp, sentBody } from '../test/utils';

const demo = {
  enabled: true,
  accounts: [
    { label: 'Customer', username: 'demo', password: 'demo-password' },
    { label: 'Librarian', username: 'demo-librarian', password: 'demo-password' },
  ],
};
const signedOut = { 'GET /api/auth/me': () => problem(401, 'Not signed in') };

describe('Login page', () => {
  it('sends signed-out visitors to the login form, with a link to register', async () => {
    mockFetch({ ...signedOut, 'GET /api/auth/demo': { enabled: false, accounts: [] } });

    renderApp('/books');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /create an account/i })).toHaveAttribute('href', '/register');
    expect(screen.queryByRole('button', { name: /try as/i })).not.toBeInTheDocument();
  });

  it('offers one button per demo account and signs in with it', async () => {
    const fetchMock = mockFetch({
      ...signedOut,
      'GET /api/auth/demo': demo,
      'POST /api/auth/login': () => jsonResponse(customer),
      'GET /api/notifications/unread-count': { count: 0 },
      'GET /api/my/loans': [],
    });

    renderApp('/login');
    expect(await screen.findByRole('button', { name: 'Try as librarian' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try as customer' }));

    expect(await screen.findByRole('heading', { name: /welcome, siti/i })).toBeInTheDocument();
    expect(sentBody(fetchMock, 'POST', '/api/auth/login')).toEqual({ username: 'demo', password: 'demo-password' });
  });

  it('shows an error for bad credentials and stays on the form', async () => {
    mockFetch({
      ...signedOut,
      'GET /api/auth/demo': { enabled: false, accounts: [] },
      'POST /api/auth/login': () => problem(401, 'Invalid username or password'),
    });

    renderApp('/login');
    await userEvent.type(await screen.findByLabelText('Username'), 'librarian');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password');
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('returns to the page that needed a sign-in, query string included', async () => {
    let signedIn = false;
    mockFetch({
      'GET /api/auth/me': () => (signedIn ? jsonResponse(customer) : problem(401, 'Not signed in')),
      'GET /api/auth/demo': { enabled: false, accounts: [] },
      'POST /api/auth/login': () => {
        signedIn = true;
        return jsonResponse(customer);
      },
      'GET /api/notifications/unread-count': { count: 0 },
      'GET /api/books': page([]),
      'GET /api/books/categories': ['Fiction', 'Poetry'],
      'GET /api/authors/options': [],
    });

    renderApp('/catalogue?category=Poetry', { withLocation: true });
    await userEvent.type(await screen.findByLabelText('Username'), 'siti');
    await userEvent.type(screen.getByLabelText('Password'), 'a-long-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Catalogue' })).toBeInTheDocument();
    await waitFor(() => expect(currentLocation()).toBe('/catalogue?category=Poetry'));
  });
});
