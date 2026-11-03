import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { customer, jsonResponse, librarian, loan, page, renderApp, sentBody, signedInAs } from '../test/utils';

const book = (overrides) => ({
  id: 3, title: 'Laskar Pelangi', publishedDate: '2005-01-01', publisherName: null,
  author: { id: 1, name: 'Andrea Hirata' }, categories: ['Fiction'],
  totalCopies: 3, availableCopies: 2, onLoan: false, ...overrides,
});

describe('Copies in the catalogue (customer)', () => {
  it('shows how many copies are on the shelf', async () => {
    signedInAs(customer, {
      'GET /api/books': page([book(), book({ id: 4, title: 'Saman', totalCopies: 1, availableCopies: 0, onLoan: true })]),
    });

    renderApp('/catalogue');

    expect(within((await screen.findByText('Laskar Pelangi')).closest('tr')).getByText('2 of 3 on shelf')).toBeInTheDocument();
    expect(within(screen.getByText('Saman').closest('tr')).getByText('On loan')).toBeInTheDocument();
  });
});

describe('Copies on the book page (librarian)', () => {
  const copies = [
    { id: 31, barcode: 'BC-AAAA1111', shelfLocation: 'FIC HIR', status: 'AVAILABLE', loan: null },
    { id: 32, barcode: 'BC-BBBB2222', shelfLocation: null, status: 'ON_LOAN',
      loan: { id: 9, memberId: 7, memberName: 'Siti', dueDate: '2026-11-15', overdue: false } },
  ];

  it('lists each copy with its borrower and adds a new copy', async () => {
    const fetchMock = signedInAs(librarian, {
      'GET /api/books/3': book(),
      'GET /api/authors/options': [{ id: 1, name: 'Andrea Hirata' }],
      'GET /api/books/categories': ['Fiction'],
      'GET /api/books/3/copies': copies,
      'POST /api/books/3/copies': () => jsonResponse({ id: 33, barcode: 'BC-NEW00001', shelfLocation: 'FIC HIR', status: 'AVAILABLE', loan: null }, 201),
    });

    renderApp('/books/3');

    const lent = (await screen.findByText('BC-BBBB2222')).closest('tr');
    expect(within(lent).getByText(/Siti/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('New copy barcode'), 'bc-new00001');
    await userEvent.type(screen.getByLabelText('Shelf location'), 'FIC HIR');
    await userEvent.click(screen.getByRole('button', { name: 'Add copy' }));
    expect(sentBody(fetchMock, 'POST', '/api/books/3/copies')).toEqual({ barcode: 'bc-new00001', shelfLocation: 'FIC HIR' });
  });
});

describe('Desk work by barcode (librarian)', () => {
  it('returns a copy by scanning its barcode', async () => {
    const fetchMock = signedInAs(librarian, {
      'GET /api/borrowed-books': page([]),
      'POST /api/borrowed-books/return-by-barcode': () =>
        jsonResponse(loan({ status: 'RETURNED', returnDate: '2026-11-03', copy: { id: 32, barcode: 'BC-BBBB2222' } })),
    });

    renderApp('/borrowed-books');
    await userEvent.type(await screen.findByLabelText('Return by barcode'), 'BC-BBBB2222{Enter}');

    expect(await screen.findByRole('status')).toHaveTextContent('Returned "Bumi Manusia" from Siti');
    expect(sentBody(fetchMock, 'POST', '/api/borrowed-books/return-by-barcode')).toEqual({ barcode: 'BC-BBBB2222' });
  });

  it('lends the scanned copy without picking the book from the list', async () => {
    const fetchMock = signedInAs(librarian, {
      'GET /api/books/options': [{ id: 3, title: 'Laskar Pelangi', authorName: 'Andrea Hirata', availableCopies: 2, totalCopies: 3, onLoan: false }],
      'GET /api/members/options': [{ id: 7, name: 'Siti (LIB-7KQ2XM)' }],
      'POST /api/borrowed-books': () => jsonResponse(loan(), 201),
      'GET /api/borrowed-books': page([]),
    });

    renderApp('/borrowed-books/new');
    await userEvent.type(await screen.findByLabelText(/Scan a barcode/), 'BC-AAAA1111');
    await userEvent.selectOptions(screen.getByLabelText('Member'), '7');
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    expect(sentBody(fetchMock, 'POST', '/api/borrowed-books')).toMatchObject({ copyBarcode: 'BC-AAAA1111', bookId: null, memberId: 7 });
  });
});
