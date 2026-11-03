import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { setLanguage } from '../i18n';
import { jsonResponse, librarian, loan, page, problem, renderApp, sentBody, signedInAs } from '../test/utils';

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

describe('Loan desk: after an action', () => {
  const secondPage = (content) => ({ content, page: 1, size: 20, totalElements: 21, totalPages: 2 });
  /** The page number of the most recent loans request. */
  const lastPage = (fetchMock) =>
    new URLSearchParams(fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/api/borrowed-books?')).at(-1).split('?')[1])
      .get('page');

  it('stays on the same page after a renewal, which leaves the loan in the list', async () => {
    const renewable = loan({ id: 43, canRenew: true });
    const fetchMock = signedInAs(librarian, {
      'GET /api/borrowed-books': secondPage([renewable]),
      'POST /api/borrowed-books/43/renew': () => jsonResponse({ ...renewable, renewals: 1 }),
    });

    renderApp('/borrowed-books?status=active');
    await screen.findByText('Bumi Manusia');
    await userEvent.click(screen.getByRole('button', { name: /Next/ }));
    await waitFor(() => expect(lastPage(fetchMock)).toBe('1'));
    const calls = fetchMock.mock.calls.length;

    await userEvent.click(within(screen.getByText('Bumi Manusia').closest('tr')).getByRole('button', { name: 'Renew' }));

    await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(calls + 1));
    expect(lastPage(fetchMock)).toBe('1');
  });

  it('steps back a page when the last loan on it leaves the filter, and focuses the page heading', async () => {
    const request = loan({ id: 31, status: 'PENDING', borrowDate: null, dueDate: null });
    const fetchMock = signedInAs(librarian, {
      'GET /api/borrowed-books': secondPage([request]),
      'POST /api/borrowed-books/31/approve': () => jsonResponse({ ...request, status: 'READY' }),
    });

    renderApp('/borrowed-books?status=pending');
    await screen.findByText('Bumi Manusia');
    await userEvent.click(screen.getByRole('button', { name: /Next/ }));
    await waitFor(() => expect(lastPage(fetchMock)).toBe('1'));

    await userEvent.click(within(screen.getByText('Bumi Manusia').closest('tr')).getByRole('button', { name: 'Approve' }));

    await waitFor(() => expect(lastPage(fetchMock)).toBe('0'));
    expect(document.activeElement).toBe(screen.getByRole('heading', { level: 1, name: 'Loans' }));
  });

  it('announces a desk return in a live region, and clears it when the next action starts or fails', async () => {
    const request = loan({ id: 31, status: 'PENDING', borrowDate: null, dueDate: null });
    signedInAs(librarian, {
      'GET /api/borrowed-books': page([request]),
      'POST /api/borrowed-books/return-by-barcode': () =>
        jsonResponse(loan({ status: 'RETURNED', copy: { id: 1, barcode: 'BC-0001AAAA' }, returnDate: '2026-11-03' })),
      'POST /api/borrowed-books/31/approve': () => problem(409, 'No copy is on the shelf.'),
    });

    renderApp('/borrowed-books');
    await screen.findByText('Bumi Manusia');
    const live = screen.getByRole('status');
    expect(live).toBeEmptyDOMElement();

    await userEvent.type(screen.getByLabelText('Return by barcode'), 'BC-0001AAAA{Enter}');
    await waitFor(() => expect(live).toHaveTextContent('Returned "Bumi Manusia" from Siti (BC-0001AAAA).'));

    await userEvent.click(within(screen.getByText('Bumi Manusia').closest('tr')).getByRole('button', { name: 'Approve' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No copy is on the shelf.');
    expect(live).toBeEmptyDOMElement();
  });
});

describe('Lending at the desk', () => {
  it('shows its validation messages in the language chosen after submitting', async () => {
    signedInAs(librarian, {
      'GET /api/books/options': [{ id: 3, title: 'Bumi Manusia', authorName: 'Pramoedya', onLoan: false }],
      'GET /api/members/options': [{ id: 7, name: 'Siti' }],
    });

    renderApp('/borrowed-books/new');
    fireEvent.submit((await screen.findByLabelText('Member')).closest('form'));
    expect(await screen.findByText('Please select a member.')).toBeInTheDocument();
    expect(screen.getByText('Scan a barcode or select a book.')).toBeInTheDocument();

    act(() => setLanguage('id'));

    expect(screen.getByText('Silakan pilih anggota.')).toBeInTheDocument();
    expect(screen.getByText('Pindai barkode atau pilih buku.')).toBeInTheDocument();
  });
});
