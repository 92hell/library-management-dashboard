import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { customer, jsonResponse, mockFetch, problem, renderApp, sentBody } from '../test/utils';

async function fillForm({ libraryCardId } = {}) {
  await userEvent.type(await screen.findByLabelText('Full name'), 'Siti Nurhaliza');
  await userEvent.type(screen.getByLabelText('Email'), 'siti@example.com');
  await userEvent.type(screen.getByLabelText('Phone'), '081234567890');
  await userEvent.type(screen.getByLabelText('Date of birth'), '1990-05-17');
  await userEvent.type(screen.getByLabelText('Username'), 'siti');
  await userEvent.type(screen.getByLabelText('Password'), 'a-long-password');
  if (libraryCardId) {
    await userEvent.click(screen.getByLabelText(/I already have a library card/i));
    await userEvent.type(screen.getByLabelText('Library card ID'), libraryCardId);
  }
}

describe('Registration page', () => {
  it('registers, signs in and lands on the customer home page', async () => {
    const fetchMock = mockFetch({
      'GET /api/auth/me': () => problem(401, 'Not signed in'),
      'POST /api/auth/register': () => jsonResponse(customer, 201),
      'POST /api/auth/login': () => jsonResponse(customer),
      'GET /api/notifications/unread-count': { count: 0 },
      'GET /api/my/loans': [],
    });

    renderApp('/register');
    await fillForm({ libraryCardId: 'LIB-7KQ2XM' });
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('heading', { name: /welcome, siti/i })).toBeInTheDocument();
    expect(sentBody(fetchMock, 'POST', '/api/auth/register')).toMatchObject({
      username: 'siti', name: 'Siti Nurhaliza', dateOfBirth: '1990-05-17', libraryCardId: 'LIB-7KQ2XM',
    });
  });

  it('shows the server error next to the library card ID field', async () => {
    mockFetch({
      'GET /api/auth/me': () => problem(401, 'Not signed in'),
      'POST /api/auth/register': () =>
        problem(400, 'No library card matches this ID with your email or phone number', {
          libraryCardId: 'No library card matches this ID with your email or phone number',
        }),
    });

    renderApp('/register');
    await fillForm({ libraryCardId: 'LIB-WRONG1' });
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('No library card matches this ID with your email or phone number', { selector: '.invalid-feedback' }))
      .toBeInTheDocument();
  });
});
