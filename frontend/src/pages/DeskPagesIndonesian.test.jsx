import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { setLanguage } from '../i18n';
import { librarian, loan, page, renderApp, signedInAs } from '../test/utils';

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

describe('Desk pages in Bahasa Indonesia', () => {
  it('shows the loans list with Indonesian filters, headers and actions', async () => {
    const request = loan({ id: 12, status: 'PENDING', borrowDate: null, dueDate: null });
    signedInAs(librarian, { 'GET /api/borrowed-books': page([request]) });
    setLanguage('id');

    renderApp('/borrowed-books');

    const row = (await screen.findByText('Bumi Manusia')).closest('tr');
    expect(screen.getByRole('heading', { name: 'Peminjaman' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Menunggu persetujuan' })).toHaveValue('pending');
    expect(screen.getByRole('option', { name: 'Siap diambil' })).toHaveValue('ready');
    expect(screen.getByRole('columnheader', { name: 'Tanggal pinjam' })).toBeInTheDocument();
    expect(row.querySelectorAll('td')[2]).toHaveTextContent('Diajukan');
    expect(within(row).getByRole('button', { name: 'Setujui' })).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Tolak' })).toBeInTheDocument();
    expect(screen.getByLabelText('Kembalikan dengan barkode')).toBeInTheDocument();
  });

  it('asks in Indonesian before deleting a loan record', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    signedInAs(librarian, { 'GET /api/borrowed-books': page([loan()]) });
    setLanguage('id');

    renderApp('/borrowed-books');

    const row = (await screen.findByText('Bumi Manusia')).closest('tr');
    expect(within(row).getByRole('button', { name: 'Tandai dikembalikan' })).toBeInTheDocument();
    await userEvent.click(within(row).getByRole('button', { name: 'Hapus' }));
    expect(window.confirm).toHaveBeenCalledWith('Hapus catatan peminjaman "Bumi Manusia" untuk Siti?');
  });

  it('shows the librarian home tiles in Indonesian', async () => {
    signedInAs(librarian, {
      'GET /api/dashboard': { pendingRequests: 2, readyForPickup: 1, activeLoans: 4, overdueLoans: 1, dueToday: 0, books: 9,
        members: 5, unpaidFines: 6000, currency: 'IDR' },
    });
    setLanguage('id');

    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Selamat datang, Lina' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /2\s*Menunggu persetujuan/ })).toHaveAttribute('href', '/borrowed-books?status=pending');
    expect(screen.getByRole('link', { name: /1\s*Peminjaman terlambat/ })).toHaveAttribute('href', '/borrowed-books?status=overdue');
    expect(screen.getByRole('link', { name: /0\s*Jatuh tempo hari ini/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /4\s*Buku dipinjam/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Rp6\.000\s*Denda belum dibayar/ })).toHaveAttribute('href', '/fines');
    expect(screen.getByRole('link', { name: 'Pinjamkan buku di meja layanan' })).toHaveAttribute('href', '/borrowed-books/new');
  });

  it('runs the fines desk in Indonesian, amounts included', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    signedInAs(librarian, { 'GET /api/fines': page([fine]) });
    setLanguage('id');

    renderApp('/fines');

    const row = (await screen.findByText('Anak Semua Bangsa')).closest('tr');
    expect(row).toHaveTextContent(/Siti.*LIB-000007.*terlambat 3 hari.*Rp3\.000.*Belum dibayar/);
    expect(screen.getByRole('option', { name: 'Belum dibayar' })).toHaveValue('UNPAID');
    expect(screen.getByRole('option', { name: 'Dihapuskan' })).toHaveValue('WAIVED');
    expect(within(row).getByRole('button', { name: 'Hapuskan' })).toBeInTheDocument();
    await userEvent.click(within(row).getByRole('button', { name: 'Catat pembayaran' }));
    expect(window.confirm).toHaveBeenCalledWith('Catat bahwa Siti telah membayar Rp3.000 untuk "Anak Semua Bangsa"?');
  });

  it('says who waived a fine in Indonesian', async () => {
    signedInAs(librarian, {
      'GET /api/fines': page([{ ...fine, status: 'WAIVED', settledBy: 'lina', note: 'Tutup saat Lebaran' }]),
    });
    setLanguage('id');

    renderApp('/fines');

    const row = (await screen.findByText('Anak Semua Bangsa')).closest('tr');
    expect(row).toHaveTextContent(/Dihapuskan oleh lina.*Tutup saat Lebaran/);
  });

  it('shows the reports in Indonesian', async () => {
    signedInAs(librarian, {
      'GET /api/reports/summary': {
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
        mostBorrowed: [{ bookId: 3, title: 'Laskar Pelangi', authorName: 'Andrea Hirata', loans: 4 }],
        mostActiveMembers: [],
      },
    });
    setLanguage('id');

    renderApp('/reports');

    expect(await screen.findByText('Buku dipinjamkan')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Laporan' })).toBeInTheDocument();
    expect(screen.getByText('Dikembalikan terlambat').closest('.card')).toHaveTextContent(/67%.*2 dari 3 peminjaman yang jatuh tempo/);
    expect(screen.getByText('Denda dikenakan').closest('.card')).toHaveTextContent(/Rp7\.000.*Rp4\.000 terkumpul/);
    expect(screen.getByText('Anggota aktif').closest('.card')).toHaveTextContent('2 baru');
    expect(screen.getByLabelText('Dari')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '30 hari terakhir' })).toBeInTheDocument();
    const top = screen.getByRole('table', { name: 'Paling sering dipinjam' });
    expect(within(top).getByRole('columnheader', { name: 'Judul' })).toBeInTheDocument();
    expect(within(top).getByRole('columnheader', { name: 'Penulis' })).toBeInTheDocument();
    expect(screen.getByText('Tidak ada yang meminjam pada periode ini.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Unduh peminjaman (CSV)' })).toBeInTheDocument();
  });
});
