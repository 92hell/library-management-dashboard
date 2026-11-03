import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button, Col, Input, Label, Row } from 'reactstrap';
import { api } from '../api';
import Availability from '../components/Availability';
import BookCover from '../components/BookCover';
import { ErrorAlert } from '../components/Feedback';
import PagedTable from '../components/PagedTable';
import SearchBox from '../components/SearchBox';
import { useAction, useApi, useDebouncedValue, usePagedList } from '../hooks';

const ORDERS = [
  { value: 'title', label: 'Title A–Z' },
  { value: 'newest', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
  { value: 'added', label: 'Recently added' },
];
const DEFAULT_ORDER = 'title';

/**
 * The catalogue as customers see it: search, filters (kept in the address so they can be shared),
 * availability and a "Request" button.
 */
export default function Catalogue() {
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const [params, setParams] = useSearchParams();
  const category = params.get('category') ?? '';
  const authorId = params.get('authorId') ?? '';
  const available = params.get('available') === 'true';
  const order = params.get('order') ?? DEFAULT_ORDER;
  const books = usePagedList('/api/books', { q, category, authorId, available: available ? 'true' : '', order });
  const categories = useApi('/api/books/categories', { inUse: 'true' });
  const authors = useApi('/api/authors/options');
  const filtered = Boolean(category || authorId || available);

  function setFilter(key, value) {
    const next = new URLSearchParams(params);
    if (!value || (key === 'order' && value === DEFAULT_ORDER)) next.delete(key);
    else next.set(key, value);
    setParams(next, { replace: true });
  }

  function clearFilters() {
    const next = new URLSearchParams();
    if (params.get('order')) next.set('order', params.get('order'));
    setParams(next, { replace: true });
  }
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
      <Row className="g-2 align-items-center mb-3">
        <Col xs={6} md={3}>
          <Label for="filter-category" className="visually-hidden">
            Category
          </Label>
          <Input id="filter-category" type="select" value={category} onChange={(e) => setFilter('category', e.target.value)}>
            <option value="">All categories</option>
            {categories.data?.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Input>
        </Col>
        <Col xs={6} md={3}>
          <Label for="filter-author" className="visually-hidden">
            Author
          </Label>
          <Input id="filter-author" type="select" value={authorId} onChange={(e) => setFilter('authorId', e.target.value)}>
            <option value="">All authors</option>
            {authors.data?.map((a) => (
              <option key={a.id} value={String(a.id)}>
                {a.name}
              </option>
            ))}
          </Input>
        </Col>
        <Col xs={6} md={3}>
          <Label for="filter-order" className="visually-hidden">
            Sort by
          </Label>
          <Input id="filter-order" type="select" value={order} onChange={(e) => setFilter('order', e.target.value)}>
            {ORDERS.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Input>
        </Col>
        <Col xs={6} md={3} className="d-flex align-items-center gap-3">
          <div className="form-check form-switch mb-0">
            <Input
              id="filter-available"
              type="checkbox"
              role="switch"
              className="form-check-input"
              checked={available}
              onChange={(e) => setFilter('available', e.target.checked ? 'true' : '')}
            />
            <Label for="filter-available" check>
              On the shelf now
            </Label>
          </div>
          {filtered && (
            <Button size="sm" color="link" className="p-0" onClick={clearFilters}>
              Clear filters
            </Button>
          )}
        </Col>
      </Row>
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
        emptyMessage={filtered ? 'No books match your search and filters.' : 'No books match your search.'}
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
