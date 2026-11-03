import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import BookCover from '../components/BookCover';
import { ErrorAlert, Loading } from '../components/Feedback';
import { useAction, useApi } from '../hooks';

/** "2 of 3 on shelf · 1 person waiting". */
export function availabilityText(book) {
  const { totalCopies: total = 0, availableCopies: available = 0, waiting } = book;
  const shelf = total === 0 ? 'No copies in the library' : available === 0 ? 'All copies are out' : `${available} of ${total} on shelf`;
  if (!waiting) return shelf;
  return `${shelf} · ${waiting} ${waiting === 1 ? 'person' : 'people'} waiting`;
}

/** A book's own page: cover, description, where to find it and whether it is in. Customers can request it. */
export default function BookDetail() {
  const { id } = useParams();
  const { isLibrarian } = useAuth();
  const book = useApi(`/api/books/${id}`);
  const request = useAction();
  const [sent, setSent] = useState(false);

  async function requestBook() {
    setSent(false);
    if (await request.run(() => api.post('/api/my/loans', { bookId: Number(id) }))) setSent(true);
  }

  if (book.error) return <ErrorAlert error={book.error} />;
  if (!book.data) return <Loading />;
  const b = book.data;
  const year = b.publishedDate?.slice(0, 4);

  return (
    <>
      <Link to={isLibrarian ? '/books' : '/catalogue'} className="small d-inline-block mb-3">
        ← {isLibrarian ? 'Books' : 'Back to the catalogue'}
      </Link>
      <Row className="g-4">
        <Col xs="auto">
          <BookCover title={b.title} src={b.coverUrl} size="lg" />
        </Col>
        <Col>
          <h1 className="h3 mb-1">{b.title}</h1>
          <p className="page-subtitle mb-3">
            by{' '}
            {isLibrarian ? <span>{b.author.name}</span> : <Link to={`/catalogue?authorId=${b.author.id}`}>{b.author.name}</Link>}
            {year && <> · {year}</>}
          </p>
          {b.description && <p className="lead fs-6 mb-4">{b.description}</p>}
          <dl className="book-details">
            <dt>Availability</dt>
            <dd>{availabilityText(b)}</dd>
            {b.shelfLocations?.length > 0 && (
              <>
                <dt>Shelved at</dt>
                <dd>{b.shelfLocations.join(', ')}</dd>
              </>
            )}
            {b.categories.length > 0 && (
              <>
                <dt>Categories</dt>
                <dd>
                  {isLibrarian
                    ? b.categories.join(', ')
                    : b.categories.map((c, i) => (
                        <span key={c}>
                          {i > 0 && ', '}
                          <Link to={`/catalogue?${new URLSearchParams({ category: c })}`}>{c}</Link>
                        </span>
                      ))}
                </dd>
              </>
            )}
            {b.publisherName && (
              <>
                <dt>Publisher</dt>
                <dd>{b.publisherName}</dd>
              </>
            )}
            {b.isbn && (
              <>
                <dt>ISBN</dt>
                <dd className="font-monospace">{b.isbn}</dd>
              </>
            )}
          </dl>
          {sent && (
            <div className="alert alert-success" role="status">
              Request sent. A librarian will review it; you will get a notification.
            </div>
          )}
          <ErrorAlert error={request.error} onDismiss={request.clearError} />
          {isLibrarian ? (
            <Link className="btn btn-primary" to={`/books/${b.id}`}>
              Edit book
            </Link>
          ) : (
            !sent && (
              <Button color="primary" onClick={requestBook} disabled={request.pending}>
                Request this book
              </Button>
            )
          )}
        </Col>
      </Row>
    </>
  );
}
