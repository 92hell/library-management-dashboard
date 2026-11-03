import { fireEvent, screen, waitFor, within } from '@testing-library/react';
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
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Request sent'));
  });

  it('announces the request in a live region that is there from the start, and moves focus to it', async () => {
    signedInAs(customer, {
      'GET /api/books/3': book,
      'POST /api/my/loans': () => jsonResponse({ id: 90, status: 'PENDING' }, 201),
    });

    renderApp('/catalogue/3');
    const button = await screen.findByRole('button', { name: 'Request this book' });
    const live = screen.getByRole('status');
    expect(live).toBeEmptyDOMElement();
    await userEvent.click(button);

    await waitFor(() => expect(live).toHaveTextContent('Request sent'));
    expect(screen.queryByRole('button', { name: 'Request this book' })).not.toBeInTheDocument();
    expect(live).toContainElement(document.activeElement);
    expect(document.activeElement).toHaveTextContent('Request sent');
  });

  it('takes a librarian back to the book list they came from', async () => {
    signedInAs(librarian, { 'GET /api/books': page([book]), 'GET /api/books/3': book });

    renderApp('/books');
    await userEvent.click(await screen.findByRole('link', { name: 'Laskar Pelangi' }));

    expect(await screen.findByRole('link', { name: '← Books' })).toHaveAttribute('href', '/books');
  });

  it('falls back to the catalogue when the page was opened directly', async () => {
    signedInAs(customer, { 'GET /api/books/3': book });

    renderApp('/catalogue/3');

    expect(await screen.findByRole('link', { name: /Back to the catalogue/ })).toHaveAttribute('href', '/catalogue');
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

  it('adds the missing author in a new tab and picks up the new author on return, keeping the form', async () => {
    let authors = formStubs['GET /api/authors/options'];
    signedInAs(librarian, {
      ...formStubs,
      'GET /api/authors/options': () => jsonResponse(authors),
      'GET /api/books/lookup': { isbn: '9789793062792', title: 'Laskar Pelangi', authorName: 'Someone New', authorId: null,
        publisherName: null, publishedDate: null, categories: [], coverUrl: null },
    });

    renderApp('/books/new');
    await userEvent.type(await screen.findByLabelText('ISBN'), '9789793062792');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));

    const link = await screen.findByRole('link', { name: 'Add the author' });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));

    authors = [...authors, { id: 10, name: 'Someone New' }];
    fireEvent.focus(window);

    expect(await screen.findByRole('option', { name: 'Someone New' })).toBeInTheDocument();
    expect(screen.getByLabelText('Title')).toHaveValue('Laskar Pelangi');
  });

  it('links the ISBN field to its hint and error, and the categories to their error', async () => {
    signedInAs(librarian, {
      ...formStubs,
      'POST /api/books': () => problem(400, 'Validation failed', { isbn: 'Not a valid ISBN.' }),
    });

    renderApp('/books/new');
    const isbn = await screen.findByLabelText('ISBN');
    expect(isbn).toHaveAccessibleDescription('Optional. Look it up to fill in the details and show the cover from Open Library.');

    fireEvent.submit(isbn.closest('form'));
    const fiction = await screen.findByLabelText('Fiction');
    await waitFor(() => expect(fiction).toHaveAccessibleDescription('Please select at least one category.'));

    await userEvent.type(isbn, '123');
    await userEvent.type(screen.getByLabelText('Title'), 'Laskar Pelangi');
    await userEvent.type(screen.getByLabelText('Published date'), '2005-01-01');
    await userEvent.selectOptions(screen.getByLabelText('Author'), 'Andrea Hirata');
    await userEvent.click(fiction);
    fireEvent.submit(isbn.closest('form'));

    await waitFor(() => expect(isbn).toHaveAccessibleDescription('Not a valid ISBN.'));
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
