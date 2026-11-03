import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { customer, librarian, renderApp, signedInAs } from '../test/utils';

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
});
