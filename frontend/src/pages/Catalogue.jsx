import { useState } from 'react';
import { Button } from 'reactstrap';
import { api } from '../api';
import Availability from '../components/Availability';
import BookCover from '../components/BookCover';
import { ErrorAlert } from '../components/Feedback';
import PagedTable from '../components/PagedTable';
import SearchBox from '../components/SearchBox';
import { useAction, useDebouncedValue, usePagedList } from '../hooks';

/** The catalogue as customers see it: search, availability and a "Request" button. */
export default function Catalogue() {
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const books = usePagedList('/api/books', { q });
  const request = useAction();
  const [sent, setSent] = useState(null);

  async function requestBook(book) {
    setSent(null);
    if (await request.run(() => api.post('/api/my/loans', { bookId: book.id }))) setSent(book.title);
  }

  return (
    <>
      <h1 className="h3 mb-1 page-title">Catalogue</h1>
      <p className="page-subtitle mb-3">Find a book and request it; we&apos;ll let you know when it&apos;s ready.</p>
      <div className="mb-3">
        <SearchBox
          id="catalogue-search"
          label="Search the catalogue"
          placeholder="Search by title, author, publisher or category"
          value={search}
          onChange={setSearch}
        />
      </div>
      {sent && (
        <div className="alert alert-success d-flex justify-content-between align-items-start" role="status">
          <span>Request sent for &quot;{sent}&quot;. A librarian will review it; you will get a notification.</span>
          <button type="button" className="btn-close" aria-label="Dismiss" onClick={() => setSent(null)} />
        </div>
      )}
      <ErrorAlert error={request.error} onDismiss={request.clearError} />
      <PagedTable
        list={books}
        headers={['Title', 'Author', 'Categories', 'Availability', '']}
        emptyMessage="No books match your search."
        renderRow={(book) => (
          <tr key={book.id}>
            <td>
              <div className="d-flex align-items-center gap-3">
                <BookCover title={book.title} />
                <span className="fw-semibold">{book.title}</span>
              </div>
            </td>
            <td>{book.author.name}</td>
            <td className="small text-muted">{book.categories.join(', ') || '—'}</td>
            <td>
              <Availability book={book} />
            </td>
            <td className="text-end">
              <Button size="sm" color="primary" outline onClick={() => requestBook(book)} disabled={request.pending}>
                Request
              </Button>
            </td>
          </tr>
        )}
      />
    </>
  );
}
