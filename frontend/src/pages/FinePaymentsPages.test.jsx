import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { customer, jsonResponse, librarian, loan, page, renderApp, sentBody, signedInAs } from '../test/utils';

const fine = {
  id: 5, loanId: 30, book: { id: 3, title: 'Anak Semua Bangsa' }, member: { id: 7, name: 'Siti' },
  libraryCardId: 'LIB-000007', amount: 3000, currency: 'IDR', daysLate: 3, dueDate: '2026-10-18',
  returnDate: '2026-10-21', status: 'UNPAID', createdAt: '2026-10-21T09:00:00Z', settledAt: null, settledBy: null,
  note: null, paidAmount: 0, outstanding: 3000, payments: [],
};
const partlyPaid = { ...fine, paidAmount: 1000, outstanding: 2000,
  payments: [{ amount: 1000, paidAt: '2026-10-22T09:00:00Z', recordedBy: 'Lina' }] };

describe('Paying a fee in parts (librarian)', () => {
  it('records part of the fee, says what is still due and offers a receipt', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue('1.000');
    const fetchMock = signedInAs(librarian, {
      'GET /api/fines': page([fine]),
      'POST /api/fines/5/pay': () => jsonResponse(partlyPaid),
      'POST /api/borrowed-books/30/receipt': () => jsonResponse({ number: 'R-2026-000042' }),
      'GET /api/receipts/R-2026-000042': { number: 'R-2026-000042', issuedAt: '2026-11-03T08:00:00Z', issuedBy: 'Lina',
        algorithm: 'Ed25519', keyId: 'k', sha256: 'a'.repeat(64), signature: 's', signedContent: '{}', verifyCode: 'c',
        verifyUrl: 'https://x/verify', qr: { size: 21, modules: '1'.repeat(441) }, genuine: true,
        content: { library: 'Community Library', loan: { status: 'RETURNED', bookTitle: 'Anak Semua Bangsa', renewals: 0 },
          events: [], fine: { amount: 3000, currency: 'IDR', status: 'UNPAID', daysLate: 3, paid: 1000, outstanding: 2000,
            payments: [{ amount: 1000, paidAt: '2026-10-22T09:00:00Z', recordedBy: 'Lina' }] }, demo: false } },
    });

    renderApp('/fines');
    await userEvent.click(within((await screen.findByText('Anak Semua Bangsa')).closest('tr'))
      .getByRole('button', { name: 'Record payment' }));

    expect(sentBody(fetchMock, 'POST', '/api/fines/5/pay')).toEqual({ amount: 1000 });
    expect(await screen.findByRole('status')).toHaveTextContent('Payment of IDR 1,000 recorded for Siti. IDR 2,000 still due.');
    await userEvent.click(screen.getByRole('button', { name: 'Print receipt' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/borrowed-books/30/receipt', expect.objectContaining({ method: 'POST' }));
    expect(await screen.findByText(/IDR 1,000.*Lina/)).toBeInTheDocument();
    expect(screen.getByText('IDR 2,000')).toBeInTheDocument();
  });

  it('refuses an amount that is not a whole number above zero, without calling the server', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue('abc');
    const fetchMock = signedInAs(librarian, { 'GET /api/fines': page([fine]) });

    renderApp('/fines');
    await userEvent.click(within((await screen.findByText('Anak Semua Bangsa')).closest('tr'))
      .getByRole('button', { name: 'Record payment' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter an amount between 1 and IDR 3,000.');
    expect(sentBody(fetchMock, 'POST', '/api/fines/5/pay')).toBeUndefined();
  });

  it('shows what is paid and what is still due on a partly paid fee', async () => {
    signedInAs(librarian, { 'GET /api/fines': page([partlyPaid]) });

    renderApp('/fines');

    expect((await screen.findByText('Anak Semua Bangsa')).closest('tr')).toHaveTextContent(/IDR 1,000 paid.*IDR 2,000 still due/);
  });
});

describe('A partly paid fee (member)', () => {
  it('shows what is still due on the loan and in the fee history', async () => {
    signedInAs(customer, {
      'GET /api/my/loans': [loan({ id: 30, status: 'RETURNED', returnDate: '2026-10-21',
        fine: { amount: 3000, currency: 'IDR', status: 'UNPAID', outstanding: 2000 } })],
      'GET /api/my/fines': { balance: 2000, currency: 'IDR', blocked: false, blockAt: 20000, fines: [partlyPaid] },
    });

    renderApp('/my/loans');

    expect(await screen.findByText('Late fee: IDR 3,000 (IDR 2,000 still due)')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Late fees' })).getByText(/IDR 2,000 still due/)).toBeInTheDocument();
  });
});
