import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { customer, jsonResponse, librarian, page, problem, renderApp, sentBody, signedInAs } from '../test/utils';

const book = {
  id: 3,
  title: 'Laskar Pelangi',
  publishedDate: '2005-01-01',
  publisherName: 'Bentang Pustaka',
  author: { id: 8, name: 'Andrea Hirata' },
  categories: ['Fiction', 'Young Adult'],
  totalCopies: 3,
  availableCopies: 0,
  onLoan: true,
  isbn: '9789793062792',
  description: 'Ten children and two devoted teachers keep a village school open.',
  coverUrl: 'https://covers.openlibrary.org/b/isbn/9789793062792-M.jpg?default=false',
  shelfLocations: ['Display', 'FIC HIR'],
  waiting: 2,
};

describe('Book page', () => {
  it('opens from the catalogue and shows the cover, description, shelves and queue', async () => {
    signedInAs(customer, { 'GET /api/books': page([{ ...book, shelfLocations: null, waiting: null }]), 'GET /api/books/3': book });

    renderApp('/catalogue');
    await userEvent.click(await screen.findByRole('link', { name: 'Laskar Pelangi' }));

    expect(await screen.findByRole('heading', { name: 'Laskar Pelangi' })).toBeInTheDocument();
    expect(screen.getByText('Ten children and two devoted teachers keep a village school open.')).toBeInTheDocument();
    expect(screen.getByText('Andrea Hirata')).toBeInTheDocument();
    expect(screen.getByText('9789793062792')).toBeInTheDocument();
    expect(screen.getByText('Display, FIC HIR')).toBeInTheDocument();
    expect(screen.getByText('All copies are out · 2 people waiting')).toBeInTheDocument();
    expect(document.querySelector('img')).toHaveAttribute('src', book.coverUrl);
  });

  it('lets a customer request the book from its page', async () => {
    const fetchMock = signedInAs(customer, {
      'GET /api/books/3': book,
      'POST /api/my/loans': () => jsonResponse({ id: 90, status: 'PENDING' }, 201),
    });

    renderApp('/catalogue/3');
    await userEvent.click(await screen.findByRole('button', { name: 'Request this book' }));

    expect(sentBody(fetchMock, 'POST', '/api/my/loans')).toEqual({ bookId: 3 });
    expect(await screen.findByRole('status')).toHaveTextContent('Request sent');
  });

  it('gives librarians an edit link instead of a request button', async () => {
    signedInAs(librarian, { 'GET /api/books/3': book });

    renderApp('/catalogue/3');

    expect(await screen.findByRole('link', { name: 'Edit book' })).toHaveAttribute('href', '/books/3');
    expect(screen.queryByRole('button', { name: 'Request this book' })).not.toBeInTheDocument();
  });
});

describe('Book form: ISBN lookup', () => {
  const formStubs = {
    'GET /api/authors/options': [{ id: 8, name: 'Andrea Hirata' }, { id: 9, name: 'Dee Lestari' }],
    'GET /api/books/categories': ['Fiction', 'Young Adult', 'Poetry'],
  };

  it('fills the form from Open Library and saves the ISBN and description', async () => {
    const fetchMock = signedInAs(librarian, {
      ...formStubs,
      'GET /api/books/lookup': {
        isbn: '9789793062792', title: 'Laskar Pelangi', authorName: 'Andrea Hirata', authorId: 8,
        publisherName: 'Bentang Pustaka', publishedDate: '2005-01-01', categories: ['Fiction', 'Young Adult'],
        coverUrl: book.coverUrl,
      },
      'POST /api/books': () => jsonResponse(book, 201),
      'GET /api/books': page([]),
    });

    renderApp('/books/new');
    await userEvent.type(await screen.findByLabelText('ISBN'), '979-3062-79-7');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));

    await waitFor(() => expect(screen.getByLabelText('Title')).toHaveValue('Laskar Pelangi'));
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/books/lookup?isbn=979-3062-79-7')).toBe(true);
    expect(screen.getByLabelText('Publisher')).toHaveValue('Bentang Pustaka');
    expect(screen.getByLabelText('Published date')).toHaveValue('2005-01-01');
    expect(screen.getByLabelText('Author')).toHaveValue('8');
    expect(screen.getByLabelText('Young Adult')).toBeChecked();
    expect(screen.getByLabelText('Poetry')).not.toBeChecked();
    expect(screen.getByLabelText('ISBN')).toHaveValue('9789793062792');
    await userEvent.type(screen.getByLabelText('Description'), 'A school on Belitung.');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(sentBody(fetchMock, 'POST', '/api/books')).toMatchObject({
      title: 'Laskar Pelangi', isbn: '9789793062792', description: 'A school on Belitung.', authorId: 8,
    });
  });

  it('names an author who is not in the catalogue yet', async () => {
    signedInAs(librarian, {
      ...formStubs,
      'GET /api/books/lookup': { isbn: '9789793062792', title: 'Laskar Pelangi', authorName: 'Someone New', authorId: null,
        publisherName: null, publishedDate: null, categories: [], coverUrl: null },
    });

    renderApp('/books/new');
    await userEvent.type(await screen.findByLabelText('ISBN'), '9789793062792');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));

    const hint = await screen.findByText(/Someone New/);
    expect(hint).toHaveTextContent('Open Library lists the author as “Someone New”, who is not in the catalogue yet.');
    expect(within(hint).getByRole('link', { name: 'Add the author' })).toHaveAttribute('href', '/authors/new');
    expect(screen.getByLabelText('Title')).toHaveValue('Laskar Pelangi');
  });

  it('shows why a lookup failed', async () => {
    signedInAs(librarian, {
      ...formStubs,
      'GET /api/books/lookup': () => problem(404, 'Open Library has no book with ISBN 9789793062792.'),
    });

    renderApp('/books/new');
    await userEvent.type(await screen.findByLabelText('ISBN'), '9789793062792');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));

    expect(await screen.findByText('Open Library has no book with ISBN 9789793062792.')).toBeInTheDocument();
  });
});
