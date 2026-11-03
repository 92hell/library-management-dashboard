import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from 'reactstrap';
import { api } from '../api';
import Availability from '../components/Availability';
import BookCover from '../components/BookCover';
import { ErrorAlert } from '../components/Feedback';
import PagedTable from '../components/PagedTable';
import PageHeader from '../components/PageHeader';
import RowActions from '../components/RowActions';
import SearchBox from '../components/SearchBox';
import { formatDate } from '../format';
import { useAction, useDebouncedValue, usePagedList } from '../hooks';

export default function BookList() {
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const books = usePagedList('/api/books', { q });
  const removal = useAction();

  async function remove(book) {
    if (!window.confirm(`Delete book "${book.title}"?`)) return;
    if (await removal.run(() => api.delete(`/api/books/${book.id}`))) books.refreshAfterRemoval();
  }

  return (
    <>
      <PageHeader title="Books" addLabel="Add book" addTo="/books/new" />
      <div className="mb-3">
        <SearchBox
          id="book-search"
          label="Search books"
          placeholder="Search by title, publisher, category or author"
          value={search}
          onChange={setSearch}
        />
      </div>
      <ErrorAlert error={removal.error} onDismiss={removal.clearError} />
      <PagedTable
        list={books}
        headers={['Title', 'Author', 'Published', 'Publisher', 'Categories', 'Copies', 'Actions']}
        emptyMessage="No books found."
        renderRow={(book) => (
          <tr key={book.id}>
            <td>
              <div className="d-flex align-items-center gap-3">
                <BookCover title={book.title} src={book.coverUrl} />
                <Link to={`/catalogue/${book.id}`} className="fw-semibold">
                  {book.title}
                </Link>
              </div>
            </td>
            <td>{book.author?.name ?? '—'}</td>
            <td className="text-nowrap">{formatDate(book.publishedDate)}</td>
            <td>{book.publisherName || '—'}</td>
            <td>
              {book.categories.map((category) => (
                <Badge key={category} color="light" className="text-dark border me-1">
                  {category}
                </Badge>
              ))}
            </td>
            <td>
              <Availability book={book} />
            </td>
            <td>
              <RowActions to={`/books/${book.id}`} onDelete={() => remove(book)} disabled={removal.pending} />
            </td>
          </tr>
        )}
      />
    </>
  );
}
