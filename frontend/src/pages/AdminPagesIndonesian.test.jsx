import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '../i18n';
import { librarian, page, renderApp, signedInAs } from '../test/utils';

const book = {
  id: 3,
  title: 'Laskar Pelangi',
  publishedDate: '2005-01-01',
  publisherName: 'Bentang Pustaka',
  author: { id: 8, name: 'Andrea Hirata' },
  categories: ['Fiction'],
  totalCopies: 3,
  availableCopies: 1,
  onLoan: false,
  coverUrl: null,
};

describe('Admin pages in Bahasa Indonesia', () => {
  it('shows the book list in Indonesian, leaving the library data as it is', async () => {
    signedInAs(librarian, { 'GET /api/books': page([book]) });
    setLanguage('id');

    renderApp('/books');

    expect(await screen.findByRole('heading', { name: 'Buku' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Tambah buku' })).toHaveAttribute('href', '/books/new');
    expect(screen.getByLabelText('Cari buku')).toHaveAttribute('placeholder', 'Cari berdasarkan judul, penerbit, kategori, atau penulis');
    expect(await screen.findByText('Laskar Pelangi')).toBeInTheDocument();
    const headers = screen.getAllByRole('columnheader').map((th) => th.textContent);
    expect(headers).toEqual(['Judul', 'Penulis', 'Terbit', 'Penerbit', 'Kategori', 'Eksemplar', 'Aksi']);
    expect(screen.getByText('Fiction')).toBeInTheDocument();
  });

  it('labels the book form in Indonesian and explains what is missing', async () => {
    signedInAs(librarian, {
      'GET /api/authors/options': [{ id: 8, name: 'Andrea Hirata' }],
      'GET /api/books/categories': ['Fiction', 'Poetry'],
    });
    setLanguage('id');

    renderApp('/books/new');

    expect(await screen.findByRole('heading', { name: 'Tambah buku' })).toBeInTheDocument();
    expect(screen.getByLabelText('ISBN')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cari' })).toBeDisabled();
    expect(screen.getByLabelText('Judul')).toBeInTheDocument();
    expect(screen.getByLabelText('Penerbit')).toBeInTheDocument();
    expect(screen.getByLabelText('Jumlah eksemplar')).toHaveValue(1);
    expect(screen.getByLabelText('Tanggal terbit')).toBeInTheDocument();
    expect(screen.getByLabelText('Deskripsi')).toBeInTheDocument();
    expect(await screen.findByRole('option', { name: 'Pilih penulis' })).toBeInTheDocument();

    // Submit the form directly: the Save button's label belongs to the shared FormActions component.
    fireEvent.submit(screen.getByLabelText('Judul').closest('form'));

    expect(await screen.findByText('Judul wajib diisi.')).toBeInTheDocument();
    expect(screen.getByText('Silakan pilih minimal satu kategori.')).toBeInTheDocument();
  });

  it('labels the member form in Indonesian', async () => {
    signedInAs(librarian);
    setLanguage('id');

    renderApp('/members/new');

    expect(await screen.findByRole('heading', { name: 'Tambah anggota' })).toBeInTheDocument();
    for (const label of ['Nama', 'Email', 'Nomor telepon', 'Tanggal lahir', 'Anggota aktif']) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    await userEvent.type(screen.getByLabelText('Nama'), 'Siti');
    fireEvent.submit(screen.getByLabelText('Nama').closest('form'));
    expect(await screen.findByText('Nomor telepon wajib diisi.')).toBeInTheDocument();
  });

  it('shows the users page in Indonesian', async () => {
    signedInAs(librarian, {
      'GET /api/users': page([
        { id: 1, username: 'lina', displayName: 'Lina', email: null, role: 'LIBRARIAN', enabled: true },
        { id: 5, username: 'budi', displayName: 'Budi', email: 'budi@example.com', role: 'CUSTOMER', enabled: false },
      ]),
    });
    setLanguage('id');

    renderApp('/users');

    expect(await screen.findByRole('heading', { name: 'Pengguna' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tambah pustakawan' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Semua peran' })).toBeInTheDocument();
    const row = (await screen.findByText('budi')).closest('tr');
    expect(within(row).getByText('Anggota')).toBeInTheDocument();
    expect(within(row).getByText('Dinonaktifkan')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Aktifkan' })).toBeInTheDocument();
    expect(screen.getAllByRole('columnheader').map((th) => th.textContent)).toEqual(['Nama pengguna', 'Nama', 'Email', 'Peran', 'Status', '']);
  });
});
