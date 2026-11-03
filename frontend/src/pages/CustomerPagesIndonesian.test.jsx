import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '../i18n';
import { customer, jsonResponse, loan, mockFetch, page, problem, renderApp, signedInAs } from '../test/utils';

const book = (overrides) => ({
  id: 3, title: 'Bumi Manusia', publishedDate: '1980-01-01', publisherName: 'Hasta Mitra',
  author: { id: 1, name: 'Pramoedya' }, categories: ['Classics'], totalCopies: 3, availableCopies: 2, onLoan: false, ...overrides,
});

describe('Customer pages in Bahasa Indonesia', () => {
  it('shows the login page in Indonesian and switches back to English', async () => {
    setLanguage('id');
    mockFetch({
      'GET /api/auth/me': () => problem(401, 'Not signed in'),
      'GET /api/auth/demo': { enabled: true, registrationOpen: true, accounts: [{ label: 'Customer', username: 'demo', password: 'pw' }] },
    });

    renderApp('/login');

    expect(await screen.findByRole('heading', { name: 'Masuk' })).toBeInTheDocument();
    expect(screen.getByLabelText('Nama pengguna')).toBeInTheDocument();
    expect(screen.getByLabelText('Kata sandi')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Buat akun' })).toHaveAttribute('href', '/register');
    expect(await screen.findByRole('button', { name: 'Coba sebagai anggota' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /EN/ }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByLabelText('Username')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('en');
  });

  it('switches to Indonesian from the register page and translates its validation', async () => {
    mockFetch({
      'GET /api/auth/me': () => problem(401, 'Not signed in'),
      'GET /api/auth/demo': { enabled: false, registrationOpen: true, accounts: [] },
    });

    renderApp('/register');
    await userEvent.click(await screen.findByRole('button', { name: /ID/ }));

    expect(await screen.findByRole('heading', { name: 'Dapatkan kartu perpustakaan Anda' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Buat akun' }));
    expect(await screen.findByText('Nama wajib diisi.')).toBeInTheDocument();
  });

  it('shows My loans with Indonesian stamps and a renew button', async () => {
    setLanguage('id');
    signedInAs(customer, {
      'GET /api/my/loans': [
        loan({ canRenew: true }),
        loan({ id: 12, overdue: true, book: { id: 4, title: 'Saman', authorName: 'Ayu Utami' } }),
        loan({ id: 13, status: 'WAITLISTED', borrowDate: null, dueDate: null, queuePosition: 2, book: { id: 5, title: 'Supernova', authorName: 'Dee' } }),
      ],
    });

    renderApp('/my/loans');

    expect(await screen.findByRole('heading', { name: 'Pinjaman saya' })).toBeInTheDocument();
    const row = screen.getByText('Bumi Manusia').closest('tr');
    expect(within(row).getByText('Dipinjam')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Perpanjang' })).toBeInTheDocument();
    expect(within(screen.getByText('Saman').closest('tr')).getByText('Terlambat')).toBeInTheDocument();
    expect(within(screen.getByText('Supernova').closest('tr')).getByText('Antrean ke-2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Batalkan permintaan' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Jatuh tempo' })).toBeInTheDocument();
  });

  it('shows the catalogue in Indonesian, without translating book data', async () => {
    setLanguage('id');
    signedInAs(customer, {
      'GET /api/books': page([book(), book({ id: 4, title: 'Saman', availableCopies: 0, onLoan: true })]),
      'POST /api/my/loans': () => jsonResponse(loan({ status: 'PENDING', borrowDate: null, dueDate: null }), 201),
    });

    renderApp('/catalogue');

    expect(await screen.findByRole('heading', { name: 'Katalog' })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Cari berdasarkan judul, penulis, penerbit, atau kategori')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Judul' })).toBeInTheDocument();
    expect(within(screen.getByText('Bumi Manusia').closest('tr')).getByText('2 dari 3 ada di rak')).toBeInTheDocument();
    expect(within(screen.getByText('Saman').closest('tr')).getByText('Dipinjam')).toBeInTheDocument();
    expect(screen.getAllByText('Classics', { selector: 'td' })).toHaveLength(2);

    await userEvent.click(within(screen.getByText('Bumi Manusia').closest('tr')).getByRole('button', { name: 'Ajukan' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Permintaan untuk "Bumi Manusia" telah dikirim.');
  });

  it('shows a book page in Indonesian', async () => {
    setLanguage('id');
    signedInAs(customer, { 'GET /api/books/3': book({ availableCopies: 0, waiting: 2, shelfLocations: ['FIC PRA'] }) });

    renderApp('/catalogue/3');

    expect(await screen.findByRole('heading', { name: 'Bumi Manusia' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Kembali ke katalog/ })).toHaveAttribute('href', '/catalogue');
    expect(screen.getByText(/^oleh/)).toHaveTextContent('oleh Pramoedya · 1980');
    expect(screen.getByText('Ketersediaan')).toBeInTheDocument();
    expect(screen.getByText('Semua eksemplar sedang dipinjam · 2 orang menunggu')).toBeInTheDocument();
    expect(screen.getByText('Lokasi rak')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ajukan peminjaman' })).toBeInTheDocument();
  });
});
