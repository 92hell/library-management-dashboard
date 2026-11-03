import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { customer, librarian, mockFetch, problem, renderApp, signedInAs } from '../test/utils';

const demoOn = {
  enabled: true,
  registrationOpen: false,
  accounts: [{ label: 'Customer', username: 'demo', password: 'demo-password' }],
};

describe('Demo mode', () => {
  it('warns on every page that this is a demo that resets', async () => {
    signedInAs(librarian, { 'GET /api/auth/demo': demoOn });

    renderApp('/');

    expect(await screen.findByText(/this is a public demo/i)).toHaveTextContent(/resets every night/i);
  });

  it('shows no banner outside demo mode', async () => {
    signedInAs(librarian, { 'GET /api/auth/demo': { enabled: false, registrationOpen: true, accounts: [] } });

    renderApp('/');

    await screen.findByRole('heading', { name: /welcome/i });
    expect(screen.queryByText(/this is a public demo/i)).not.toBeInTheDocument();
  });

  it('explains that registration is off instead of showing the form', async () => {
    mockFetch({ 'GET /api/auth/me': () => problem(401, 'Not signed in'), 'GET /api/auth/demo': demoOn });

    renderApp('/register');

    expect(await screen.findByText(/registration is turned off in this demo/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Create account' })).not.toBeInTheDocument();
  });

  it('demo accounts see their profile but cannot edit it or change the password', async () => {
    signedInAs({ ...customer, demoAccount: true }, {
      'GET /api/auth/demo': demoOn,
      'GET /api/profile': { id: 2, username: 'demo', role: 'CUSTOMER', displayName: 'Dewi', email: 'd@x.io', member: null },
    });

    renderApp('/profile');

    expect(await screen.findByText(/shared demo account/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Change password' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save details' })).not.toBeInTheDocument();
  });

  it('hides account administration from the demo librarian', async () => {
    signedInAs({ ...librarian, demoAccount: true }, { 'GET /api/auth/demo': demoOn });

    renderApp('/');

    const nav = await screen.findByRole('navigation');
    expect(within(nav).getByRole('link', { name: 'Loans' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Users' })).not.toBeInTheDocument();
  });
});
