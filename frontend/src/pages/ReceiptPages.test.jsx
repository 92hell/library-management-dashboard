import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { setLanguage } from '../i18n';
import { customer, jsonResponse, librarian, loan, page, problem, renderApp, signedInAs, mockFetch } from '../test/utils';

const content = {
  number: 'R-2026-000042',
  library: 'Community Library',
  issuedAt: '2026-11-03T08:00:00Z',
  issuedBy: 'lina',
  loan: {
    id: 11, status: 'RETURNED', bookTitle: 'Bumi Manusia', authorName: 'Pramoedya Ananta Toer', copyBarcode: 'BC-0001AAAA',
    memberName: 'Siti Rahma', libraryCardId: 'LIB-000007', requestedAt: '2026-10-01T09:00:00Z', borrowDate: '2026-10-02',
    dueDate: '2026-10-16', returnDate: '2026-10-18', renewals: 0,
  },
  events: [
    { type: 'REQUESTED', status: 'PENDING', occurredAt: '2026-10-01T09:00:00Z', actor: 'siti', detail: null },
    { type: 'APPROVED', status: 'READY', occurredAt: '2026-10-01T10:00:00Z', actor: 'lina', detail: 'Copy BC-0001AAAA set aside until 2026-10-04' },
    { type: 'RETURNED', status: 'RETURNED', occurredAt: '2026-10-18T11:00:00Z', actor: 'lina', detail: 'Copy BC-0001AAAA' },
    { type: 'FINE_CHARGED', status: 'RETURNED', occurredAt: '2026-10-18T11:00:00Z', actor: 'lina', detail: 'Late fee IDR 2,000 for 2 days late' },
  ],
  fine: { amount: 2000, currency: 'IDR', status: 'PAID', daysLate: 2 },
};
const receipt = {
  number: 'R-2026-000042',
  issuedAt: '2026-11-03T08:00:00Z',
  issuedBy: 'lina',
  algorithm: 'Ed25519',
  keyId: '0123456789abcdef',
  sha256: 'a'.repeat(64),
  signature: 'c2lnbmF0dXJl',
  signedContent: '{}',
  content,
  verifyCode: 'AbCdEfGhIjKl',
  verifyUrl: 'https://library.example/verify/R-2026-000042?code=AbCdEfGhIjKl',
  qr: { size: 21, modules: '1'.repeat(21 * 21) },
  genuine: true,
};

describe('Loan receipts', () => {
  it('a librarian opens a printable receipt from the loans list', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    const fetchMock = signedInAs(librarian, {
      'GET /api/borrowed-books': page([loan({ id: 11, status: 'RETURNED', returnDate: '2026-10-18' })]),
      'POST /api/borrowed-books/11/receipt': () => jsonResponse(receipt),
      'GET /api/receipts/R-2026-000042': receipt,
    });

    renderApp('/borrowed-books');
    await userEvent.click(within((await screen.findByText('Bumi Manusia')).closest('tr')).getByRole('button', { name: 'Receipt' }));

    expect(fetchMock).toHaveBeenCalledWith('/api/borrowed-books/11/receipt', expect.objectContaining({ method: 'POST' }));
    expect(await screen.findByRole('heading', { name: /Loan receipt/ })).toBeInTheDocument();
    expect(screen.getByText('R-2026-000042')).toBeInTheDocument();
    expect(screen.getByText('Siti Rahma')).toBeInTheDocument();
    const history = screen.getByRole('table', { name: 'History' });
    expect(within(history).getAllByRole('row')).toHaveLength(5);
    expect(within(history).getByText('Late fee charged')).toBeInTheDocument();
    expect(screen.getByText(/IDR 2,000.*paid/i)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'QR code: scan to check this receipt' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Stamp: Community Library/ })).toBeInTheDocument();
    expect(screen.getByText('a'.repeat(64))).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Download PDF' })).toHaveAttribute('href', '/api/receipts/R-2026-000042.pdf');

    await userEvent.click(screen.getByRole('button', { name: 'Print' }));
    expect(print).toHaveBeenCalled();
  });

  it('a customer gets a receipt for their own loan', async () => {
    const fetchMock = signedInAs(customer, {
      'GET /api/my/loans': [loan({ id: 11 })],
      'POST /api/my/loans/11/receipt': () => jsonResponse(receipt),
      'GET /api/receipts/R-2026-000042': receipt,
    });

    renderApp('/my/loans');
    await userEvent.click(within((await screen.findByText('Bumi Manusia')).closest('tr')).getByRole('button', { name: 'Receipt' }));

    expect(fetchMock).toHaveBeenCalledWith('/api/my/loans/11/receipt', expect.objectContaining({ method: 'POST' }));
    expect(await screen.findByText('R-2026-000042')).toBeInTheDocument();
  });

  it('shows the receipt in Bahasa Indonesia', async () => {
    setLanguage('id');
    signedInAs(librarian, { 'GET /api/receipts/R-2026-000042': receipt });

    renderApp('/receipts/R-2026-000042');

    expect(await screen.findByRole('heading', { name: /Bukti peminjaman/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cetak' })).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Riwayat' })).getByText('Denda dikenakan')).toBeInTheDocument();
  });
});

describe('Checking a receipt (public)', () => {
  const verification = (overrides = {}) => ({
    genuine: true, number: 'R-2026-000042', library: 'Community Library', issuedAt: '2026-11-03T08:00:00Z',
    bookTitle: 'Bumi Manusia', copyBarcode: 'BC-0001AAAA', memberName: 'S**i R***a', status: 'RETURNED',
    borrowDate: '2026-10-02', dueDate: '2026-10-16', returnDate: '2026-10-18',
    events: [{ type: 'REQUESTED', occurredAt: '2026-10-01T09:00:00Z' }, { type: 'RETURNED', occurredAt: '2026-10-18T11:00:00Z' }],
    fine: null, algorithm: 'Ed25519', keyId: '0123456789abcdef', sha256: 'a'.repeat(64), ...overrides,
  });

  it('confirms a genuine receipt without signing in, and shows the record with the name masked', async () => {
    const fetchMock = mockFetch({
      'GET /api/auth/me': () => problem(401, 'Not signed in'),
      'GET /api/auth/demo': { enabled: false, registrationOpen: true, accounts: [] },
      'GET /api/receipts/verify/R-2026-000042': verification(),
    });

    renderApp('/verify/R-2026-000042?code=AbCdEfGhIjKl');

    expect(await screen.findByRole('status')).toHaveTextContent('Genuine receipt');
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/receipts/verify/R-2026-000042?code=AbCdEfGhIjKl')).toBe(true);
    expect(screen.getByText('S**i R***a')).toBeInTheDocument();
    expect(screen.getByText('Bumi Manusia')).toBeInTheDocument();
  });

  it('warns loudly when the record does not match its signature', async () => {
    mockFetch({
      'GET /api/auth/me': () => problem(401, 'Not signed in'),
      'GET /api/auth/demo': { enabled: false, registrationOpen: true, accounts: [] },
      'GET /api/receipts/verify/R-2026-000042': verification({ genuine: false }),
    });

    renderApp('/verify/R-2026-000042?code=AbCdEfGhIjKl');

    expect(await screen.findByRole('alert')).toHaveTextContent('does not match');
  });

  it('says so when the number or code is wrong', async () => {
    mockFetch({
      'GET /api/auth/me': () => problem(401, 'Not signed in'),
      'GET /api/auth/demo': { enabled: false, registrationOpen: true, accounts: [] },
      'GET /api/receipts/verify/R-2026-000042': () => problem(404, 'No receipt has this number and code.'),
    });

    renderApp('/verify/R-2026-000042?code=wrong');

    expect(await screen.findByRole('alert')).toHaveTextContent('No receipt has this number and code.');
  });
});
