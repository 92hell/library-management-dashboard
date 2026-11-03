import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { customer, jsonResponse, librarian, loan, page, renderApp, sentBody, signedInAs } from '../test/utils';

const queued = loan({ id: 41, status: 'WAITLISTED', borrowDate: null, dueDate: null, queuePosition: 2,
  book: { id: 5, title: 'Saman', authorName: 'Ayu Utami' } });
const ready = loan({ id: 42, status: 'READY', borrowDate: null, dueDate: null, readyUntil: '2026-11-06',
  copy: { id: 9, barcode: 'BC-HOLD0001' }, book: { id: 6, title: 'Supernova', authorName: 'Dee' } });
const renewable = loan({ id: 43, canRenew: true, renewals: 0 });

describe('Holds and renewals (customer)', () => {
  it('shows queue position and pickup date, renews a loan and cancels a hold', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fetchMock = signedInAs(customer, {
      'GET /api/my/loans': [queued, ready, renewable],
      'POST /api/my/loans/43/renew': () => jsonResponse({ ...renewable, dueDate: '2026-11-29', renewals: 1, canRenew: false }),
      'POST /api/my/loans/42/cancel': () => jsonResponse({ ...ready, status: 'CANCELLED' }),
    });

    renderApp('/my/loans');

    expect(await screen.findByText('#2 in queue')).toBeInTheDocument();
    const readyRow = screen.getByText('Supernova').closest('tr');
    expect(within(readyRow).getByText(/Collect by/)).toBeInTheDocument();
    await userEvent.click(within(screen.getByText('Bumi Manusia').closest('tr')).getByRole('button', { name: 'Renew' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/my/loans/43/renew', expect.objectContaining({ method: 'POST' }));
    await userEvent.click(within(readyRow).getByRole('button', { name: 'Cancel hold' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/my/loans/42/cancel', expect.objectContaining({ method: 'POST' }));
  });

  it('tells the customer on the home page that a book is waiting for them', async () => {
    signedInAs(customer, { 'GET /api/my/loans': [ready] });

    renderApp('/');

    expect(await screen.findByText(/ready for pickup/i)).toHaveTextContent('Supernova');
  });
});

describe('Holds shelf (librarian)', () => {
  it('hands over a copy that was set aside', async () => {
    const fetchMock = signedInAs(librarian, {
      'GET /api/borrowed-books': page([ready]),
      'POST /api/borrowed-books/42/checkout': () => jsonResponse({ ...ready, status: 'ACTIVE' }),
    });

    renderApp('/borrowed-books?status=ready');

    expect(await screen.findByLabelText('Status')).toHaveValue('ready');
    await userEvent.click(within((await screen.findByText('Supernova')).closest('tr')).getByRole('button', { name: 'Hand over' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/borrowed-books/42/checkout', expect.objectContaining({ method: 'POST' }));
  });

  it('renews a loan at the desk', async () => {
    const fetchMock = signedInAs(librarian, {
      'GET /api/borrowed-books': page([renewable]),
      'POST /api/borrowed-books/43/renew': () => jsonResponse({ ...renewable, renewals: 1 }),
    });

    renderApp('/borrowed-books');
    await userEvent.click(within((await screen.findByText('Bumi Manusia')).closest('tr')).getByRole('button', { name: 'Renew' }));

    expect(sentBody(fetchMock, 'POST', '/api/borrowed-books/43/renew')).toBeNull();
  });
});
