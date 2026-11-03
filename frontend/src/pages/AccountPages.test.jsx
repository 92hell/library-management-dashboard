import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '../i18n';
import { customer, problem, renderApp, signedInAs } from '../test/utils';

const profile = {
  id: 2, username: 'siti', role: 'CUSTOMER', displayName: 'Siti', email: 'siti@example.com',
  member: { id: 7, libraryCardId: 'LIB-7KQ2XM', name: 'Siti', email: 'siti@example.com', phone: '0812', dateOfBirth: '1990-05-17', active: true },
};

describe('Profile page', () => {
  it('shows the library card code and reports a wrong current password', async () => {
    signedInAs(customer, {
      'GET /api/profile': profile,
      'PUT /api/profile/password': () => problem(400, 'Validation failed', { currentPassword: 'is not correct' }),
    });

    renderApp('/profile');

    expect(await screen.findByText('LIB-7KQ2XM')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Current password'), 'nope');
    await userEvent.type(screen.getByLabelText('New password'), 'a-brand-new-password');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('is not correct')).toBeInTheDocument();
  });

  it('checks the new password length in the language chosen afterwards', async () => {
    signedInAs(customer, { 'GET /api/profile': profile });

    renderApp('/profile');
    await userEvent.type(await screen.findByLabelText('Current password'), 'old-password');
    await userEvent.type(screen.getByLabelText('New password'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('Use at least 10 characters.')).toBeInTheDocument();

    act(() => setLanguage('id'));

    expect(screen.getByText('Gunakan minimal 10 karakter.')).toBeInTheDocument();
  });
});

describe('Notifications page', () => {
  it('lists notifications and marks them all read', async () => {
    const fetchMock = signedInAs(customer, {
      'GET /api/notifications': {
        content: [{ id: 1, type: 'DUE_SOON', message: '"Saman" is due on 2026-11-05.', link: '/my/loans', read: false, createdAt: '2026-11-03T08:00:00Z' }],
        page: 0, size: 20, totalElements: 1, totalPages: 1,
      },
      'POST /api/notifications/read-all': () => new Response(null, { status: 204 }),
    });

    renderApp('/notifications');

    expect(await screen.findByText('"Saman" is due on 2026-11-05.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Mark all as read' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/notifications/read-all', expect.objectContaining({ method: 'POST' }));
  });
});
