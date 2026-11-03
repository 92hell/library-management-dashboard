import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { customer, jsonResponse, librarian, problem, renderApp, signedInAs } from '../test/utils';

describe('Navigation by role', () => {
  it('librarians get the back-office sections and a notification count', async () => {
    signedInAs(librarian, { 'GET /api/notifications/unread-count': { count: 3 } });

    renderApp('/');

    const nav = await screen.findByRole('navigation');
    for (const name of ['Loans', 'Books', 'Authors', 'Members', 'Users']) {
      expect(within(nav).getByRole('link', { name })).toBeInTheDocument();
    }
    expect(await within(nav).findByRole('link', { name: /notifications \(3 unread\)/i })).toBeInTheDocument();
  });

  it('customers get the catalogue and their loans, but no staff sections', async () => {
    signedInAs(customer);

    renderApp('/');

    const nav = await screen.findByRole('navigation');
    expect(within(nav).getByRole('link', { name: 'Catalogue' })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'My loans' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Members' })).not.toBeInTheDocument();
  });

  it('customers opening a staff page are sent to their home page', async () => {
    signedInAs(customer);

    renderApp('/members');

    expect(await screen.findByRole('heading', { name: /welcome, siti/i })).toBeInTheDocument();
  });

  it('after logging out, the next sign-in starts on the home page, not the previous user\'s page', async () => {
    let signedIn = true;
    signedInAs(customer, {
      'GET /api/auth/me': () => (signedIn ? jsonResponse(customer) : problem(401, 'Not signed in')),
      'GET /api/profile': { id: 2, username: 'siti', role: 'CUSTOMER', displayName: 'Siti', email: 's@x.io', member: null },
      'POST /api/auth/logout': () => {
        signedIn = false;
        return new Response(null, { status: 204 });
      },
      'GET /api/auth/demo': { enabled: false, accounts: [] },
      'POST /api/auth/login': () => jsonResponse(librarian),
    });

    renderApp('/profile');
    await userEvent.click(await screen.findByRole('button', { name: 'Log out' }));
    await userEvent.type(await screen.findByLabelText('Username'), 'lina');
    await userEvent.type(screen.getByLabelText('Password'), 'secret-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: /welcome, lina/i })).toBeInTheDocument();
  });
});
