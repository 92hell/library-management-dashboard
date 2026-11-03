import { Link } from 'react-router-dom';
import { Alert, Card, CardBody, Col, Row } from 'reactstrap';
import { useAuth } from '../auth';
import { ErrorAlert } from '../components/Feedback';
import FineBalance from '../components/FineBalance';
import { formatDate } from '../format';
import { useApi } from '../hooks';
import { useT } from '../i18n';

export default function CustomerHome() {
  const { user } = useAuth();
  const { data: loans = [], error: loansError } = useApi('/api/my/loans');
  const { data: fines, error: finesError } = useApi('/api/my/fines');
  const active = loans.filter((loan) => loan.status === 'ACTIVE');
  const pending = loans.filter((loan) => ['PENDING', 'WAITLISTED'].includes(loan.status));
  const overdue = active.filter((loan) => loan.overdue);
  const ready = loans.filter((loan) => loan.status === 'READY');
  const t = useT();

  return (
    <>
      <h1 className="h3 mb-1">{t('Welcome, {name}', { name: user.displayName })}</h1>
      <p className="page-subtitle mb-4">{t('Good to see you at the library. Find your next read, or check on the ones you have.')}</p>
      <ErrorAlert error={loansError} />
      <ErrorAlert error={finesError} />
      {ready.length > 0 && (
        <Alert color="success">
          {t('Ready for pickup at the desk:')}{' '}
          {ready.map((loan, i) => (
            <span key={loan.id}>
              {i > 0 && ', '}
              <strong>{loan.book.title}</strong> ({t('collect by {date}', { date: formatDate(loan.readyUntil) })})
            </span>
          ))}
          .
        </Alert>
      )}
      <FineBalance fines={fines} />
      {overdue.length > 0 && (
        <Alert color="danger">
          {overdue.length === 1
            ? t('You have 1 overdue book. Please return it as soon as possible.')
            : t('You have {count} overdue books. Please return them as soon as possible.', { count: overdue.length })}
        </Alert>
      )}
      <Row xs="1" md="3" className="g-3 mb-4">
        <Col>
          <Card className="h-100 shadow-sm tile">
            <CardBody>
              <span className="d-block display-6 fw-semibold">{active.length}</span>
              <span className="small">{t('Books with you')}</span>
            </CardBody>
          </Card>
        </Col>
        <Col>
          <Card className="h-100 shadow-sm tile">
            <CardBody>
              <span className="d-block display-6 fw-semibold">{pending.length}</span>
              <span className="small">{t('Requests waiting for a librarian')}</span>
            </CardBody>
          </Card>
        </Col>
        <Col>
          <Card className="h-100 shadow-sm tile">
            <CardBody>
              <span className="d-block fw-semibold">{t('Next due')}</span>
              <span className="small">
                {active.length
                  ? formatDate([...active].sort((a, b) => a.dueDate.localeCompare(b.dueDate))[0].dueDate)
                  : t('Nothing due')}
              </span>
            </CardBody>
          </Card>
        </Col>
      </Row>
      <div className="d-flex flex-wrap gap-2">
        <Link className="btn btn-primary" to="/catalogue">
          {t('Browse the catalogue')}
        </Link>
        <Link className="btn btn-outline-secondary" to="/my/loans">
          {t('My loans')}
        </Link>
      </div>
    </>
  );
}
