import { Link } from 'react-router-dom';
import { ListGroup, ListGroupItem } from 'reactstrap';
import { formatDate } from '../format';
import { useApi } from '../hooks';
import { useT } from '../i18n';
import { ErrorAlert, Loading } from './Feedback';
import LoanStatus from './LoanStatus';

/** The borrowing history of one member. */
export default function MemberLoans({ memberId, flush = false }) {
  const { data: loans, error, loading } = useApi(`/api/members/${memberId}/loans`);
  const t = useT();

  if (error) return <ErrorAlert error={error} />;
  if (loading) return <Loading label={t('Loading loans…')} />;
  if (loans.length === 0) return <p className="text-muted mb-0">{t('This member has not borrowed any books yet.')}</p>;

  return (
    <ListGroup flush={flush}>
      {loans.map((loan) => (
        <ListGroupItem key={loan.id} className="d-flex flex-wrap justify-content-between gap-2">
          <span>
            <Link to={`/borrowed-books/${loan.id}`} className="fw-semibold">
              {loan.book.title}
            </Link>
            {loan.book.authorName && <span className="text-muted"> {t('by {author}', { author: loan.book.authorName })}</span>}
          </span>
          <span className="small">
            {loan.borrowDate ? t('Borrowed {date}', { date: formatDate(loan.borrowDate) }) : t('Requested')} · <LoanStatus loan={loan} />
          </span>
        </ListGroupItem>
      ))}
    </ListGroup>
  );
}
