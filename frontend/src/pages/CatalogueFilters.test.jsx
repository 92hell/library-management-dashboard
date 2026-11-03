import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { currentLocation, customer, jsonResponse, loan, page, renderApp, signedInAs } from '../test/utils';

const laskar = {
  id: 3, title: 'Laskar Pelangi', publishedDate: '2005-01-01', publisherName: null, author: { id: 8, name: 'Andrea Hirata' },
  categories: ['Fiction', 'Young Adult'], totalCopies: 3, availableCopies: 2, onLoan: false, isbn: null, description: null,
  coverUrl: null, shelfLocations: ['FIC HIR'], waiting: 0,
};

const stubs = {
  'GET /api/books': page([laskar]),
  'GET /api/books/categories': ['Fiction', 'Poetry', 'Young Adult'],
  'GET /api/authors/options': [{ id: 8, name: 'Andrea Hirata' }, { id: 9, name: 'Dee Lestari' }],
  'GET /api/books/3': laskar,
};

/** The filters of the most recent catalogue request, as an object (paging left out). */
function lastQuery(fetchMock) {
  const urls = fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/api/books?') || url === '/api/books');
  const query = new URLSearchParams(urls.at(-1).split('?')[1] ?? '');
  query.delete('page');
  query.delete('size');
  return Object.fromEntries(query);
}

describe('Catalogue filters', () => {
  it('sorts by title by default and narrows by category, author and what is on the shelf', async () => {
    const fetchMock = signedInAs(customer, stubs);

    renderApp('/catalogue');
    await screen.findByText('Laskar Pelangi');
    expect(lastQuery(fetchMock)).toEqual({ order: 'title' });
    expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/books/categories?inUse=true')).toBe(true);

    await userEvent.selectOptions(screen.getByLabelText('Category'), 'Poetry');
    await userEvent.selectOptions(screen.getByLabelText('Author'), 'Dee Lestari');
    await userEvent.click(screen.getByLabelText('On the shelf now'));
    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'Newest first');

    await waitFor(() =>
      expect(lastQuery(fetchMock)).toEqual({ category: 'Poetry', authorId: '9', available: 'true', order: 'newest' }),
    );
  });

  it('opens with the filters from the address, and clears them', async () => {
    const fetchMock = signedInAs(customer, stubs);

    renderApp('/catalogue?category=Fiction&authorId=8&available=true');

    await screen.findByText('Laskar Pelangi');
    expect(screen.getByLabelText('Category')).toHaveValue('Fiction');
    await waitFor(() => expect(screen.getByLabelText('Author')).toHaveValue('8'));
    expect(screen.getByLabelText('On the shelf now')).toBeChecked();
    expect(lastQuery(fetchMock)).toEqual({ category: 'Fiction', authorId: '8', available: 'true', order: 'title' });

    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));

    await waitFor(() => expect(lastQuery(fetchMock)).toEqual({ order: 'title' }));
    expect(screen.getByLabelText('Category')).toHaveValue('');
    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument();
  });

  it('links a book page\'s categories and author back to the filtered catalogue', async () => {
    signedInAs(customer, stubs);

    renderApp('/catalogue/3');

    expect(await screen.findByRole('link', { name: 'Young Adult' })).toHaveAttribute('href', '/catalogue?category=Young+Adult');
    expect(screen.getByRole('link', { name: 'Andrea Hirata' })).toHaveAttribute('href', '/catalogue?authorId=8');
  });

  it('ignores an unknown sort order and a non-numeric author in the address', async () => {
    const fetchMock = signedInAs(customer, stubs);

    renderApp('/catalogue?order=cheapest&authorId=abc&category=Fiction', { withLocation: true });

    await screen.findByText('Laskar Pelangi');
    expect(screen.getByLabelText('Sort by')).toHaveValue('title');
    expect(screen.getByLabelText('Author')).toHaveValue('');
    const sent = fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/api/books?'));
    expect(sent.some((url) => url.includes('cheapest') || url.includes('authorId=abc'))).toBe(false);
    expect(lastQuery(fetchMock)).toEqual({ category: 'Fiction', order: 'title' });

    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(currentLocation()).toBe('/catalogue'));
  });

  it('keeps the search text in the address, so it is still there after leaving and coming back', async () => {
    const fetchMock = signedInAs(customer, stubs);

    renderApp('/catalogue?q=pelangi', { withLocation: true });

    expect(await screen.findByLabelText('Search the catalogue')).toHaveValue('pelangi');
    await waitFor(() => expect(lastQuery(fetchMock)).toEqual({ q: 'pelangi', order: 'title' }));

    await userEvent.clear(screen.getByLabelText('Search the catalogue'));
    await userEvent.type(screen.getByLabelText('Search the catalogue'), 'laskar');
    await waitFor(() => expect(currentLocation()).toBe('/catalogue?q=laskar'));
    expect(lastQuery(fetchMock)).toEqual({ q: 'laskar', order: 'title' });

    await userEvent.click(screen.getByRole('link', { name: 'Laskar Pelangi' }));
    await userEvent.click(await screen.findByRole('link', { name: /Back to the catalogue/ }));

    expect(await screen.findByLabelText('Search the catalogue')).toHaveValue('laskar');
    expect(currentLocation()).toBe('/catalogue?q=laskar');
  });

  it('keeps every filter change, even several in a row, and the switch stays as clicked', async () => {
    signedInAs(customer, stubs);

    renderApp('/catalogue', { withLocation: true });
    await screen.findByText('Laskar Pelangi');
    await screen.findByRole('option', { name: 'Dee Lestari' });

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'Poetry' } });
    fireEvent.change(screen.getByLabelText('Author'), { target: { value: '9' } });
    fireEvent.click(screen.getByLabelText('On the shelf now'));
    expect(screen.getByLabelText('On the shelf now')).toBeChecked();

    await waitFor(() => expect(currentLocation()).toBe('/catalogue?category=Poetry&authorId=9&available=true'));
    expect(screen.getByLabelText('Category')).toHaveValue('Poetry');
    expect(screen.getByLabelText('Author')).toHaveValue('9');
    expect(screen.getByLabelText('On the shelf now')).toBeChecked();
  });

  it('follows the address when it changes from outside, e.g. the menu link', async () => {
    signedInAs(customer, stubs);

    renderApp('/catalogue?category=Fiction&available=true&q=laskar', { withLocation: true });
    expect(await screen.findByLabelText('Search the catalogue')).toHaveValue('laskar');
    expect(screen.getByLabelText('On the shelf now')).toBeChecked();

    await userEvent.click(within(screen.getByRole('navigation')).getByRole('link', { name: 'Catalogue' }));

    await waitFor(() => expect(screen.getByLabelText('Search the catalogue')).toHaveValue(''));
    expect(screen.getByLabelText('Category')).toHaveValue('');
    expect(screen.getByLabelText('On the shelf now')).not.toBeChecked();
    expect(currentLocation()).toBe('/catalogue');
  });

  it('returns from a book page to the catalogue with the same filters', async () => {
    signedInAs(customer, stubs);

    renderApp('/catalogue?category=Fiction&order=newest');
    await userEvent.click(await screen.findByRole('link', { name: 'Laskar Pelangi' }));

    expect(await screen.findByRole('link', { name: /Back to the catalogue/ }))
      .toHaveAttribute('href', '/catalogue?category=Fiction&order=newest');
  });

  it('names the actions column and announces a sent request in a live region', async () => {
    signedInAs(customer, {
      ...stubs,
      'POST /api/my/loans': () => jsonResponse(loan({ status: 'PENDING', borrowDate: null, dueDate: null }), 201),
    });

    renderApp('/catalogue');
    await screen.findByText('Laskar Pelangi');

    expect(screen.getAllByRole('columnheader').at(-1)).toHaveAccessibleName('Actions');
    const live = screen.getByRole('status');
    expect(live).toBeEmptyDOMElement();
    await userEvent.click(screen.getByRole('button', { name: 'Request' }));
    await waitFor(() => expect(live).toHaveTextContent('Request sent for "Laskar Pelangi"'));
    expect(screen.getByRole('status')).toBe(live);
  });
});
