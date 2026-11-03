import { Link } from 'react-router-dom';
import { Alert, Card, CardBody, Col, Row } from 'reactstrap';
import { useAuth } from '../auth';
import { formatDate } from '../format';
import { useApi } from '../hooks';

export default function CustomerHome() {
  const { user } = useAuth();
  const { data: loans = [] } = useApi('/api/my/loans');
  const active = loans.filter((loan) => loan.status === 'ACTIVE');
  const pending = loans.filter((loan) => ['PENDING', 'WAITLISTED'].includes(loan.status));
  const overdue = active.filter((loan) => loan.overdue);
  const ready = loans.filter((loan) => loan.status === 'READY');

  return (
    <>
      <h1 className="h3 mb-1">Welcome, {user.displayName}</h1>
      <p className="page-subtitle mb-4">Good to see you at the library. Find your next read, or check on the ones you have.</p>
      {ready.length > 0 && (
        <Alert color="success">
          Ready for pickup at the desk:{' '}
          {ready.map((loan, i) => (
            <span key={loan.id}>
              {i > 0 && ', '}
              <strong>{loan.book.title}</strong> (collect by {formatDate(loan.readyUntil)})
            </span>
          ))}
          .
        </Alert>
      )}
      {overdue.length > 0 && (
        <Alert color="danger">
          You have {overdue.length} overdue {overdue.length === 1 ? 'book' : 'books'}. Please return{' '}
          {overdue.length === 1 ? 'it' : 'them'} as soon as possible.
        </Alert>
      )}
      <Row xs="1" md="3" className="g-3 mb-4">
        <Col>
          <Card className="h-100 shadow-sm tile">
            <CardBody>
              <span className="d-block display-6 fw-semibold">{active.length}</span>
              <span className="small">Books with you</span>
            </CardBody>
          </Card>
        </Col>
        <Col>
          <Card className="h-100 shadow-sm tile">
            <CardBody>
              <span className="d-block display-6 fw-semibold">{pending.length}</span>
              <span className="small">Requests waiting for a librarian</span>
            </CardBody>
          </Card>
        </Col>
        <Col>
          <Card className="h-100 shadow-sm tile">
            <CardBody>
              <span className="d-block fw-semibold">Next due</span>
              <span className="small">
                {active.length
                  ? formatDate([...active].sort((a, b) => a.dueDate.localeCompare(b.dueDate))[0].dueDate)
                  : 'Nothing due'}
              </span>
            </CardBody>
          </Card>
        </Col>
      </Row>
      <div className="d-flex flex-wrap gap-2">
        <Link className="btn btn-primary" to="/catalogue">
          Browse the catalogue
        </Link>
        <Link className="btn btn-outline-secondary" to="/my/loans">
          My loans
        </Link>
      </div>
    </>
  );
}
