import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { customer, jsonResponse, loan, page, problem, renderApp, sentBody, signedInAs } from '../test/utils';

const book = (overrides) => ({
  id: 3, title: 'Bumi Manusia', publishedDate: '1980-01-01', publisherName: null,
  author: { id: 1, name: 'Pramoedya' }, categories: ['Classics'], totalCopies: 1, availableCopies: 1, onLoan: false, ...overrides,
});

describe('Catalogue (customer)', () => {
  it('requests a book and confirms it', async () => {
    const fetchMock = signedInAs(customer, {
      'GET /api/books': page([book(), book({ id: 4, title: 'Saman', availableCopies: 0, onLoan: true })]),
      'POST /api/my/loans': () => jsonResponse(loan({ status: 'PENDING', borrowDate: null, dueDate: null }), 201),
    });

    renderApp('/catalogue');
    const row = (await screen.findByText('Saman')).closest('tr');
    expect(within(row).getByText('On loan')).toBeInTheDocument();
    await userEvent.click(within(screen.getByText('Bumi Manusia').closest('tr')).getByRole('button', { name: 'Request' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Request sent for "Bumi Manusia"'));
    expect(sentBody(fetchMock, 'POST', '/api/my/loans')).toEqual({ bookId: 3 });
  });
});

describe('My loans (customer)', () => {
  it('shows each loan with its status and lets the customer cancel a pending request', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const pending = loan({ id: 21, status: 'PENDING', borrowDate: null, dueDate: null, book: { id: 5, title: 'Saman', authorName: 'Ayu' } });
    const fetchMock = signedInAs(customer, {
      'GET /api/my/loans': [pending, loan({ overdue: true }), loan({ id: 12, status: 'REJECTED', decisionNote: 'Damaged copy' })],
      'POST /api/my/loans/21/cancel': () => jsonResponse({ ...pending, status: 'CANCELLED' }),
    });

    renderApp('/my/loans');

    expect(await screen.findByText('Overdue')).toBeInTheDocument();
    expect(screen.getByText(/Damaged copy/)).toBeInTheDocument();
    await userEvent.click(within(screen.getByText('Saman').closest('tr')).getByRole('button', { name: 'Cancel request' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/my/loans/21/cancel', expect.objectContaining({ method: 'POST' }));
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('heading', { level: 1, name: 'My loans' })));
  });
});

describe('Customer home', () => {
  it('says so when the loans cannot be loaded, instead of showing zero', async () => {
    signedInAs(customer, { 'GET /api/my/loans': () => problem(500, 'Loans are unavailable.') });

    renderApp('/');

    expect(await screen.findByRole('alert')).toHaveTextContent('Loans are unavailable.');
  });

  it('says so when the fines cannot be loaded', async () => {
    signedInAs(customer, { 'GET /api/my/fines': () => problem(500, 'Fines are unavailable.') });

    renderApp('/');

    expect(await screen.findByRole('alert')).toHaveTextContent('Fines are unavailable.');
  });
});
