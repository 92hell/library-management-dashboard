import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import BookCover from '../components/BookCover';
import { ErrorAlert, Loading } from '../components/Feedback';
import { useAction, useApi } from '../hooks';
import { translate, useT } from '../i18n';

/** "2 of 3 on shelf · 1 person waiting", in the language of `t` (a translate function, e.g. from useT). */
export function availabilityText(book, t = translate) {
  const { totalCopies: total = 0, availableCopies: available = 0, waiting } = book;
  const shelf =
    total === 0
      ? t('No copies in the library')
      : available === 0
        ? t('All copies are out')
        : t('{available} of {total} on shelf', { available, total });
  if (!waiting) return shelf;
  return `${shelf} · ${waiting === 1 ? t('1 person waiting') : t('{count} people waiting', { count: waiting })}`;
}

/** Puts `element` where `{name}` is in an already translated sentence, so the sentence can be reordered freely. */
function withElement(text, name, element) {
  const [before, after = ''] = text.split(`{${name}}`);
  return (
    <>
      {before}
      {element}
      {after}
    </>
  );
}

/** A book's own page: cover, description, where to find it and whether it is in. Customers can request it. */
export default function BookDetail() {
  const { id } = useParams();
  const { isLibrarian } = useAuth();
  const book = useApi(`/api/books/${id}`);
  const request = useAction();
  const [sent, setSent] = useState(false);
  const t = useT();

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
        ← {isLibrarian ? t('Books') : t('Back to the catalogue')}
      </Link>
      <Row className="g-4">
        <Col xs="auto">
          <BookCover title={b.title} src={b.coverUrl} size="lg" />
        </Col>
        <Col>
          <h1 className="h3 mb-1">{b.title}</h1>
          <p className="page-subtitle mb-3">
            {withElement(
              t('by {author}'),
              'author',
              isLibrarian ? <span>{b.author.name}</span> : <Link to={`/catalogue?authorId=${b.author.id}`}>{b.author.name}</Link>,
            )}
            {year && <> · {year}</>}
          </p>
          {b.description && <p className="lead fs-6 mb-4">{b.description}</p>}
          <dl className="book-details">
            <dt>{t('Availability')}</dt>
            <dd>{availabilityText(b, t)}</dd>
            {b.shelfLocations?.length > 0 && (
              <>
                <dt>{t('Shelved at')}</dt>
                <dd>{b.shelfLocations.join(', ')}</dd>
              </>
            )}
            {b.categories.length > 0 && (
              <>
                <dt>{t('Categories')}</dt>
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
                <dt>{t('Publisher')}</dt>
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
              {t('Request sent. A librarian will review it; you will get a notification.')}
            </div>
          )}
          <ErrorAlert error={request.error} onDismiss={request.clearError} />
          {isLibrarian ? (
            <Link className="btn btn-primary" to={`/books/${b.id}`}>
              {t('Edit book')}
            </Link>
          ) : (
            !sent && (
              <Button color="primary" onClick={requestBook} disabled={request.pending}>
                {t('Request this book')}
              </Button>
            )
          )}
        </Col>
      </Row>
    </>
  );
}
