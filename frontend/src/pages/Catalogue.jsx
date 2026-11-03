import { useEffect, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { Button, Col, Input, Label, Row } from 'reactstrap';
import { api } from '../api';
import Availability from '../components/Availability';
import BookCover from '../components/BookCover';
import { ErrorAlert } from '../components/Feedback';
import PagedTable from '../components/PagedTable';
import SearchBox from '../components/SearchBox';
import { useAction, useApi, usePagedList } from '../hooks';
import { msg, useT } from '../i18n';

const ORDERS = [
  { value: 'title', label: msg('Title A–Z') },
  { value: 'newest', label: msg('Newest first') },
  { value: 'oldest', label: msg('Oldest first') },
  { value: 'added', label: msg('Recently added') },
];
const DEFAULT_ORDER = 'title';
const SEARCH_DELAY = 300;
/** The address parameters this page owns; anything else in the query string is left alone. */
const OWN_PARAMS = ['q', 'category', 'authorId', 'available', 'order'];

/**
 * The filters in the address, cleaned up: an unknown order sorts by title and a non-numeric author
 * means all authors, so neither is ever sent to the API. `search` is the text in the search box,
 * `q` the search that is applied (the same text, once the user stops typing).
 */
function readFilters(params) {
  const order = params.get('order');
  const authorId = params.get('authorId') ?? '';
  const q = (params.get('q') ?? '').trim();
  return {
    search: q,
    q,
    category: params.get('category') ?? '',
    authorId: /^\d+$/.test(authorId) ? authorId : '',
    available: params.get('available') === 'true',
    order: ORDERS.some(({ value }) => value === order) ? order : DEFAULT_ORDER,
  };
}

/** `params` with this page's parameters set from `filters` (defaults and empty values left out). */
function writeFilters(params, filters) {
  const next = new URLSearchParams(params);
  OWN_PARAMS.forEach((key) => next.delete(key));
  if (filters.q) next.set('q', filters.q);
  if (filters.category) next.set('category', filters.category);
  if (filters.authorId) next.set('authorId', filters.authorId);
  if (filters.available) next.set('available', 'true');
  if (filters.order !== DEFAULT_ORDER) next.set('order', filters.order);
  return next;
}

/**
 * The catalogue as customers see it: search, filters (kept in the address so they can be shared,
 * and survive going to a book and back), availability and a "Request" button.
 *
 * The controls show local state rather than the address: react-router applies address changes in a
 * transition, so controls bound to it directly would briefly show the old value (a switch unticks,
 * then ticks again) and quick successive changes could overwrite each other. The local state is
 * written to the address, and follows it when the address changes from outside (back/forward, links).
 */
export default function Catalogue() {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const [state, setState] = useState(() => ({ ...readFilters(params), url: params.toString() }));
  const url = params.toString();
  const wanted = writeFilters(params, state).toString();
  if (url !== state.url) {
    // Our own write arriving matches what we want; anything else came from outside, so follow it.
    setState(url === wanted ? { ...state, url } : { ...readFilters(params), url });
  }
  const { search, q, category, authorId, available, order } = state;

  // Apply the search once the user stops typing.
  useEffect(() => {
    if (search.trim() === q) return undefined;
    const timer = setTimeout(() => setState((current) => ({ ...current, q: current.search.trim() })), SEARCH_DELAY);
    return () => clearTimeout(timer);
  }, [search, q]);

  // Keep the address in step with the filters (replacing the entry, so Back leaves the catalogue).
  useEffect(() => {
    if (wanted !== url) setParams((prev) => writeFilters(prev, state), { replace: true });
  }, [wanted, url, state, setParams]);

  const books = usePagedList('/api/books', { q, category, authorId, available: available ? 'true' : '', order });
  const categories = useApi('/api/books/categories', { inUse: 'true' });
  const authors = useApi('/api/authors/options');
  const filtered = Boolean(category || authorId || available);
  const t = useT();
  // Book pages link back here with the same search and filters.
  const backState = { from: location.pathname + location.search };

  function setFilter(key, value) {
    setState((current) => ({ ...current, [key]: value }));
  }

  function clearFilters() {
    setState((current) => ({ ...current, category: '', authorId: '', available: false }));
  }
  const request = useAction();
  const [sent, setSent] = useState(null);

  async function requestBook(book) {
    setSent(null);
    if (await request.run(() => api.post('/api/my/loans', { bookId: book.id }))) setSent(book.title);
  }

  return (
    <>
      <h1 className="h3 mb-1 page-title">{t('Catalogue')}</h1>
      <p className="page-subtitle mb-3">{t('Find a book and request it; we\'ll let you know when it\'s ready.')}</p>
      <div className="mb-3">
        <SearchBox
          id="catalogue-search"
          label={t('Search the catalogue')}
          placeholder={t('Search by title, author, publisher or category')}
          value={search}
          onChange={(value) => setFilter('search', value)}
        />
      </div>
      <Row className="g-2 align-items-center mb-3">
        <Col xs={6} md={3}>
          <Label for="filter-category" className="visually-hidden">
            {t('Category')}
          </Label>
          <Input id="filter-category" type="select" value={category} onChange={(e) => setFilter('category', e.target.value)}>
            <option value="">{t('All categories')}</option>
            {categories.data?.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Input>
        </Col>
        <Col xs={6} md={3}>
          <Label for="filter-author" className="visually-hidden">
            {t('Author')}
          </Label>
          <Input id="filter-author" type="select" value={authorId} onChange={(e) => setFilter('authorId', e.target.value)}>
            <option value="">{t('All authors')}</option>
            {authors.data?.map((a) => (
              <option key={a.id} value={String(a.id)}>
                {a.name}
              </option>
            ))}
          </Input>
        </Col>
        <Col xs={6} md={3}>
          <Label for="filter-order" className="visually-hidden">
            {t('Sort by')}
          </Label>
          <Input id="filter-order" type="select" value={order} onChange={(e) => setFilter('order', e.target.value)}>
            {ORDERS.map(({ value, label }) => (
              <option key={value} value={value}>
                {t(label)}
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
              onChange={(e) => setFilter('available', e.target.checked)}
            />
            <Label for="filter-available" check>
              {t('On the shelf now')}
            </Label>
          </div>
          {filtered && (
            <Button size="sm" color="link" className="p-0" onClick={clearFilters}>
              {t('Clear filters')}
            </Button>
          )}
        </Col>
      </Row>
      <div role="status">
        {sent && (
          <div className="alert alert-success d-flex justify-content-between align-items-start">
            <span>{t('Request sent for "{title}". A librarian will review it; you will get a notification.', { title: sent })}</span>
            <button type="button" className="btn-close" aria-label={t('Dismiss')} onClick={() => setSent(null)} />
          </div>
        )}
      </div>
      <ErrorAlert error={request.error} onDismiss={request.clearError} />
      <PagedTable
        list={books}
        headers={[t('Title'), t('Author'), t('Categories'), t('Availability'), <span className="visually-hidden">{t('Actions')}</span>]}
        emptyMessage={filtered ? t('No books match your search and filters.') : t('No books match your search.')}
        renderRow={(book) => (
          <tr key={book.id}>
            <td>
              <div className="d-flex align-items-center gap-3">
                <BookCover title={book.title} src={book.coverUrl} />
                <Link to={`/catalogue/${book.id}`} state={backState} className="fw-semibold">
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
                {t('Request')}
              </Button>
            </td>
          </tr>
        )}
      />
    </>
  );
}
