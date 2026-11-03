import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '../i18n';
import { librarian, renderApp, signedInAs } from '../test/utils';

const summary = (overrides = {}) => ({
  from: '2026-10-05',
  to: '2026-11-03',
  loans: 12,
  returns: 9,
  dueInPeriod: 3,
  lateInPeriod: 2,
  lateRate: 0.667,
  activeMembers: 5,
  newMembers: 2,
  finesCharged: 7000,
  finesCollected: 4000,
  currency: 'IDR',
  mostBorrowed: [
    { bookId: 3, title: 'Laskar Pelangi', authorName: 'Andrea Hirata', loans: 4 },
    { bookId: 4, title: 'Bumi Manusia', authorName: 'Pramoedya Ananta Toer', loans: 2 },
  ],
  mostActiveMembers: [{ memberId: 7, name: 'Siti', libraryCardId: 'LIB-000007', loans: 3 }],
  ...overrides,
});

function summaryCalls(fetchMock) {
  return fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/api/reports/summary'));
}

describe('Reports', () => {
  it('shows lending totals, the late-return rate, fines and the rankings', async () => {
    signedInAs(librarian, { 'GET /api/reports/summary': summary() });

    renderApp('/reports');

    expect(await screen.findByText('Books lent')).toBeInTheDocument();
    expect(screen.getByText('Books lent').closest('.card')).toHaveTextContent('12');
    expect(screen.getByText('Returned late').closest('.card')).toHaveTextContent(/67%.*2 of 3 loans that fell due/);
    expect(screen.getByText('Fines charged').closest('.card')).toHaveTextContent(/IDR 7,000.*IDR 4,000 collected/);
    const top = screen.getByRole('table', { name: 'Most borrowed' });
    expect(within(top).getByRole('link', { name: 'Laskar Pelangi' })).toHaveAttribute('href', '/catalogue/3');
    expect(within(top).getByText('Laskar Pelangi').closest('tr')).toHaveTextContent(/1.*Laskar Pelangi.*Andrea Hirata.*4/);
    const members = screen.getByRole('table', { name: 'Most active members' });
    expect(within(members).getByRole('link', { name: 'Siti' })).toHaveAttribute('href', '/members/7');
    expect(screen.getByText(/Oct 5, 2026.*Nov 3, 2026/)).toBeInTheDocument();
  });

  it('reports on the chosen dates and downloads CSVs for the same range', async () => {
    const fetchMock = signedInAs(librarian, { 'GET /api/reports/summary': summary() });

    renderApp('/reports');
    await screen.findByText('Books lent');
    const from = screen.getByLabelText('From');
    await userEvent.clear(from);
    await userEvent.type(from, '2026-01-01');
    const to = screen.getByLabelText('To');
    await userEvent.clear(to);
    await userEvent.type(to, '2026-06-30');

    await waitFor(() => expect(summaryCalls(fetchMock).at(-1)).toBe('/api/reports/summary?from=2026-01-01&to=2026-06-30'));
    expect(screen.getByRole('link', { name: 'Download loans (CSV)' }))
      .toHaveAttribute('href', '/api/reports/loans.csv?from=2026-01-01&to=2026-06-30');
    expect(screen.getByRole('link', { name: 'Download most borrowed (CSV)' }))
      .toHaveAttribute('href', '/api/reports/most-borrowed.csv?from=2026-01-01&to=2026-06-30');
  });

  it('says so when nothing fell due, instead of a 0% late rate', async () => {
    signedInAs(librarian, { 'GET /api/reports/summary': summary({ dueInPeriod: 0, lateInPeriod: 0, lateRate: null }) });

    renderApp('/reports');

    expect(await screen.findByText('Returned late')).toBeInTheDocument();
    expect(screen.getByText('Returned late').closest('.card')).toHaveTextContent(/—.*Nothing fell due/);
  });

  it('formats the tile numbers for the language', async () => {
    signedInAs(librarian, { 'GET /api/reports/summary': summary({ loans: 12345, returns: 2048, activeMembers: 1500, newMembers: 1200 }) });

    renderApp('/reports');

    expect(await screen.findByText('Books lent')).toBeInTheDocument();
    expect(screen.getByText('Books lent').closest('.card')).toHaveTextContent('12,345');
    expect(screen.getByText('Active members').closest('.card')).toHaveTextContent(/1,500.*1,200 new/);

    act(() => setLanguage('id'));
    expect(screen.getByText('Buku dipinjamkan').closest('.card')).toHaveTextContent('12.345');
    expect(screen.getByText('Anggota aktif').closest('.card')).toHaveTextContent(/1\.500.*1\.200 baru/);
    expect(screen.getByText('Dikembalikan').closest('.card')).toHaveTextContent('2.048');
  });

  it('is in the librarian menu', async () => {
    signedInAs(librarian);

    renderApp('/');

    expect(await screen.findByRole('link', { name: 'Reports' })).toHaveAttribute('href', '/reports');
  });
});
