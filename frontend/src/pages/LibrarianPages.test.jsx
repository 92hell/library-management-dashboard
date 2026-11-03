import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { jsonResponse, librarian, loan, page, renderApp, sentBody, signedInAs } from '../test/utils';

describe('Librarian home', () => {
  it('shows the dashboard numbers with a shortcut to pending requests', async () => {
    signedInAs(librarian, {
      'GET /api/dashboard': { pendingRequests: 2, activeLoans: 5, overdueLoans: 1, dueToday: 0, books: 40, members: 12 },
    });

    renderApp('/');

    const pending = await screen.findByRole('link', { name: /2\s*pending requests/i });
    expect(pending).toHaveAttribute('href', '/borrowed-books?status=pending');
  });
});

describe('Loan queue (librarian)', () => {
  const request = loan({ id: 31, status: 'PENDING', borrowDate: null, dueDate: null });

  it('opens on the filter from the URL and approves a request', async () => {
    const fetchMock = signedInAs(librarian, {
      'GET /api/borrowed-books': page([request]),
      'POST /api/borrowed-books/31/approve': () => jsonResponse({ ...request, status: 'ACTIVE' }),
    });

    renderApp('/borrowed-books?status=pending');

    expect(await screen.findByLabelText('Status')).toHaveValue('pending');
    await userEvent.click(within((await screen.findByText('Bumi Manusia')).closest('tr')).getByRole('button', { name: 'Approve' }));
    expect(sentBody(fetchMock, 'POST', '/api/borrowed-books/31/approve')).toEqual({});
  });

  it('rejects a request with the reason the librarian typed', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue('Reserved for a class');
    const fetchMock = signedInAs(librarian, {
      'GET /api/borrowed-books': page([request]),
      'POST /api/borrowed-books/31/reject': () => jsonResponse({ ...request, status: 'REJECTED' }),
    });

    renderApp('/borrowed-books?status=pending');
    await userEvent.click(within((await screen.findByText('Bumi Manusia')).closest('tr')).getByRole('button', { name: 'Reject' }));

    expect(sentBody(fetchMock, 'POST', '/api/borrowed-books/31/reject')).toEqual({ reason: 'Reserved for a class' });
  });

  it('marks an active loan as returned', async () => {
    const fetchMock = signedInAs(librarian, {
      'GET /api/borrowed-books': page([loan()]),
      'POST /api/borrowed-books/11/return': () => jsonResponse(loan({ status: 'RETURNED', returnDate: '2026-11-03' })),
    });

    renderApp('/borrowed-books');
    await userEvent.click(within((await screen.findByText('Bumi Manusia')).closest('tr')).getByRole('button', { name: 'Mark returned' }));

    expect(fetchMock).toHaveBeenCalledWith('/api/borrowed-books/11/return', expect.objectContaining({ method: 'POST' }));
  });
});
