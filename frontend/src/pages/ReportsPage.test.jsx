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
  weekly: [
    { start: '2026-10-05', end: '2026-10-11', loans: 3 },
    { start: '2026-10-12', end: '2026-10-18', loans: 0 },
    { start: '2026-10-19', end: '2026-10-25', loans: 7 },
    { start: '2026-10-26', end: '2026-11-01', loans: 2 },
    { start: '2026-11-02', end: '2026-11-03', loans: 1 },
  ],
  monthly: [
    { start: '2026-10-05', end: '2026-10-31', onTime: 6, late: 2 },
    { start: '2026-11-01', end: '2026-11-03', onTime: 1, late: 0 },
  ],
  ...overrides,
});

/** The stat tile with this label (the same words also appear in the charts). */
async function tile(label) {
  const labels = await screen.findAllByText(label);
  return labels.find((el) => el.closest('.tile')).closest('.card');
}

function summaryCalls(fetchMock) {
  return fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/api/reports/summary'));
}

describe('Reports', () => {
  it('shows lending totals, the late-return rate, fines and the rankings', async () => {
    signedInAs(librarian, { 'GET /api/reports/summary': summary() });

    renderApp('/reports');

    expect(await tile('Books lent')).toHaveTextContent('12');
    expect(await tile('Returned late')).toHaveTextContent(/67%.*2 of 3 loans that fell due/);
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
    await tile('Books lent');
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

    expect(await tile('Returned late')).toHaveTextContent(/—.*Nothing fell due/);
  });

  it('formats the tile numbers for the language', async () => {
    signedInAs(librarian, { 'GET /api/reports/summary': summary({ loans: 12345, returns: 2048, activeMembers: 1500, newMembers: 1200 }) });

    renderApp('/reports');

    expect(await tile('Books lent')).toHaveTextContent('12,345');
    expect(screen.getByText('Active members').closest('.card')).toHaveTextContent(/1,500.*1,200 new/);

    act(() => setLanguage('id'));
    expect(await tile('Buku dipinjamkan')).toHaveTextContent('12.345');
    expect(screen.getByText('Anggota aktif').closest('.card')).toHaveTextContent(/1\.500.*1\.200 baru/);
    expect(screen.getByText('Dikembalikan').closest('.card')).toHaveTextContent('2.048');
  });

  it('is in the librarian menu', async () => {
    signedInAs(librarian);

    renderApp('/');

    expect(await screen.findByRole('link', { name: 'Reports' })).toHaveAttribute('href', '/reports');
  });
});

describe('Report charts', () => {
  it('draws books lent per week and returns per month, with a legend only where there are two series', async () => {
    signedInAs(librarian, { 'GET /api/reports/summary': summary() });

    renderApp('/reports');

    const weekly = await screen.findByRole('figure', { name: 'Books lent per week' });
    const monthly = screen.getByRole('figure', { name: 'Returns per month' });
    expect(within(weekly).queryByRole('list', { name: 'Legend' })).not.toBeInTheDocument();
    const legend = within(monthly).getByRole('list', { name: 'Legend' });
    expect(within(legend).getAllByRole('listitem').map((li) => li.textContent)).toEqual(['Returned on time', 'Returned late']);
  });

  it('has the same numbers as a table', async () => {
    signedInAs(librarian, { 'GET /api/reports/summary': summary() });

    renderApp('/reports');
    const monthly = await screen.findByRole('figure', { name: 'Returns per month' });
    await userEvent.click(within(monthly).getByText('Show the numbers'));

    const rows = within(within(monthly).getByRole('table')).getAllByRole('row');
    expect(rows[0]).toHaveTextContent('PeriodReturned on timeReturned late');
    expect(rows[1]).toHaveTextContent('Oct 5, 2026 – Oct 31, 2026');
    expect(rows[1]).toHaveTextContent(/62$/);
  });

  it('reads one column at a time with the arrow keys', async () => {
    signedInAs(librarian, { 'GET /api/reports/summary': summary() });

    renderApp('/reports');
    const chart = await screen.findByRole('group', { name: /Books lent per week: use the left and right arrow keys/ });
    chart.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(within(chart).getByText('Oct 5, 2026 – Oct 11, 2026')).toBeVisible();
    expect(within(chart).getByText('3')).toBeInTheDocument();
    await userEvent.keyboard('{End}');
    expect(within(chart).getByText('Nov 2, 2026 – Nov 3, 2026')).toBeInTheDocument();
    await userEvent.keyboard('{ArrowLeft}');
    expect(within(chart).getByText('Oct 26, 2026 – Nov 1, 2026')).toBeInTheDocument();
  });

  it('reads in Bahasa Indonesia', async () => {
    act(() => setLanguage('id'));
    signedInAs(librarian, { 'GET /api/reports/summary': summary() });

    renderApp('/reports');

    expect(await screen.findByRole('figure', { name: 'Buku dipinjamkan per minggu' })).toBeInTheDocument();
    expect(screen.getByRole('figure', { name: 'Pengembalian per bulan' })).toHaveTextContent('Dikembalikan tepat waktu');
    act(() => setLanguage('en'));
  });
});
