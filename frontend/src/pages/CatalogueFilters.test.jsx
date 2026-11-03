import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { customer, page, renderApp, signedInAs } from '../test/utils';

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
});
