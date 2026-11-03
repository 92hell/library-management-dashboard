import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { customer, jsonResponse, librarian, loan, page, problem, renderApp, sentBody, signedInAs } from '../test/utils';

const fine = {
  id: 5,
  loanId: 30,
  book: { id: 3, title: 'Anak Semua Bangsa' },
  member: { id: 7, name: 'Siti' },
  libraryCardId: 'LIB-000007',
  amount: 3000,
  currency: 'IDR',
  daysLate: 3,
  dueDate: '2026-10-18',
  returnDate: '2026-10-21',
  status: 'UNPAID',
  createdAt: '2026-10-21T09:00:00Z',
  settledAt: null,
  settledBy: null,
  note: null,
};
const myFines = (overrides = {}) => ({ balance: 3000, currency: 'IDR', blocked: false, blockAt: 20000, fines: [fine], ...overrides });
const overdue = loan({ id: 44, overdue: true, dueDate: '2026-10-28', fine: { amount: 6000, currency: 'IDR', status: 'ACCRUING' } });

describe('Late fees (customer)', () => {
  it('shows the fee building up on an overdue book, the balance owed and the fee history', async () => {
    signedInAs(customer, { 'GET /api/my/loans': [overdue], 'GET /api/my/fines': myFines() });

    renderApp('/my/loans');

    const row = (await screen.findByText('Bumi Manusia')).closest('tr');
    expect(within(row).getByText('Late fee so far: IDR 6,000')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('You owe IDR 3,000 in late fees. Please pay at the desk.');
    const history = screen.getByRole('table', { name: 'Late fees' });
    expect(within(history).getByText('Anak Semua Bangsa').closest('tr')).toHaveTextContent(/3 days late.*IDR 3,000.*Unpaid/);
  });

  it('explains that borrowing is paused while the balance is too high', async () => {
    signedInAs(customer, { 'GET /api/my/fines': myFines({ balance: 25000, blocked: true }) });

    renderApp('/my/loans');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You owe IDR 25,000 in late fees. You can borrow again once it is paid at the desk.',
    );
  });

  it('reminds the customer of unpaid fees on the home page', async () => {
    signedInAs(customer, { 'GET /api/my/fines': myFines() });

    renderApp('/');

    expect(await screen.findByText(/You owe IDR 3,000 in late fees/)).toBeInTheDocument();
  });

  it('says so when the fees cannot be loaded', async () => {
    signedInAs(customer, { 'GET /api/my/loans': [loan()], 'GET /api/my/fines': () => problem(500, 'Fines are unavailable.') });

    renderApp('/my/loans');

    expect(await screen.findByRole('alert')).toHaveTextContent('Fines are unavailable.');
    expect(screen.getByText('Bumi Manusia')).toBeInTheDocument();
  });

  it('says nothing about fees when nothing is owed', async () => {
    signedInAs(customer, { 'GET /api/my/loans': [loan()] });

    renderApp('/my/loans');

    await screen.findByText('Bumi Manusia');
    expect(screen.queryByText(/late fees/)).not.toBeInTheDocument();
  });
});

describe('Fines desk (librarian)', () => {
  it('lists unpaid fines first and records a payment', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fetchMock = signedInAs(librarian, {
      'GET /api/fines': page([fine]),
      'POST /api/fines/5/pay': () => jsonResponse({ ...fine, status: 'PAID' }),
    });

    renderApp('/fines');

    const row = (await screen.findByText('Anak Semua Bangsa')).closest('tr');
    expect(row).toHaveTextContent(/Siti.*LIB-000007.*3 days late.*IDR 3,000/);
    expect(screen.getByLabelText('Status')).toHaveValue('UNPAID');
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('status=UNPAID'))).toBe(true);
    await userEvent.click(within(row).getByRole('button', { name: 'Record payment' }));
    expect(window.confirm).toHaveBeenCalledWith('Record that Siti paid IDR 3,000 for "Anak Semua Bangsa"?');
    expect(sentBody(fetchMock, 'POST', '/api/fines/5/pay')).toBeNull();
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('heading', { level: 1, name: 'Fines' })));
  });

  it('waives a fine only with a reason', async () => {
    const prompt = vi.spyOn(window, 'prompt').mockReturnValueOnce('   ').mockReturnValueOnce('Closed for Lebaran');
    const fetchMock = signedInAs(librarian, {
      'GET /api/fines': page([fine]),
      'POST /api/fines/5/waive': () => jsonResponse({ ...fine, status: 'WAIVED' }),
    });

    renderApp('/fines');
    const waive = within((await screen.findByText('Anak Semua Bangsa')).closest('tr')).getByRole('button', { name: 'Waive' });

    await userEvent.click(waive);
    expect(sentBody(fetchMock, 'POST', '/api/fines/5/waive')).toBeUndefined();
    expect(screen.getByRole('alert')).toHaveTextContent('A reason is required to waive a fine.');
    await userEvent.click(waive);
    expect(prompt).toHaveBeenCalledTimes(2);
    expect(sentBody(fetchMock, 'POST', '/api/fines/5/waive')).toEqual({ reason: 'Closed for Lebaran' });
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });

  it('says nothing when the librarian cancels the waive prompt', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue(null);
    const fetchMock = signedInAs(librarian, { 'GET /api/fines': page([fine]) });

    renderApp('/fines');
    await userEvent.click(within((await screen.findByText('Anak Semua Bangsa')).closest('tr')).getByRole('button', { name: 'Waive' }));

    expect(sentBody(fetchMock, 'POST', '/api/fines/5/waive')).toBeUndefined();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows who settled a fine and why it was waived', async () => {
    signedInAs(librarian, {
      'GET /api/fines': page([{ ...fine, status: 'WAIVED', settledBy: 'lina', note: 'Closed for Lebaran' }]),
    });

    renderApp('/fines');

    const row = (await screen.findByText('Anak Semua Bangsa')).closest('tr');
    expect(row).toHaveTextContent(/Waived by lina.*Closed for Lebaran/);
    expect(within(row).queryByRole('button', { name: 'Record payment' })).not.toBeInTheDocument();
  });

  it('shows the unpaid total on the dashboard and links to the fines desk', async () => {
    signedInAs(librarian, {
      'GET /api/dashboard': { pendingRequests: 0, activeLoans: 0, overdueLoans: 0, dueToday: 0, books: 0, members: 0,
        unpaidFines: 6000, currency: 'IDR' },
    });

    renderApp('/');

    expect(await screen.findByRole('link', { name: /IDR 6,000\s*Unpaid fines/ })).toHaveAttribute('href', '/fines');
    expect(screen.getByRole('link', { name: 'Fines' })).toHaveAttribute('href', '/fines');
  });
});
