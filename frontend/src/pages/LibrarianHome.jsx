import { Link } from 'react-router-dom';
import { Card, CardBody, Col, Row } from 'reactstrap';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import { formatMoney } from '../format';
import { useApi } from '../hooks';

const TILES = [
  { key: 'pendingRequests', label: 'Pending requests', to: '/borrowed-books?status=pending', highlight: 'primary' },
  { key: 'readyForPickup', label: 'Ready for pickup', to: '/borrowed-books?status=ready', highlight: 'primary' },
  { key: 'overdueLoans', label: 'Overdue loans', to: '/borrowed-books?status=overdue', highlight: 'danger' },
  { key: 'dueToday', label: 'Due today', to: '/borrowed-books?status=active' },
  { key: 'activeLoans', label: 'Books on loan', to: '/borrowed-books?status=active' },
  { key: 'books', label: 'Books', to: '/books' },
  { key: 'members', label: 'Members', to: '/members' },
  { key: 'unpaidFines', label: 'Unpaid fines', to: '/fines', highlight: 'danger', money: true },
];

export default function LibrarianHome() {
  const { user } = useAuth();
  const summary = useApi('/api/dashboard');

  return (
    <>
      <h1 className="h3 mb-1">Welcome, {user.displayName}</h1>
      <p className="page-subtitle mb-4">Here is what needs your attention at the desk today.</p>
      <ErrorAlert error={summary.error} />
      {summary.loading && !summary.data ? (
        <Loading />
      ) : (
        <Row xs="2" md="4" lg="4" className="g-3">
          {TILES.map(({ key, label, to, highlight, money }) => {
            const value = summary.data?.[key] ?? 0;
            const shown = money ? formatMoney(value, summary.data?.currency ?? '') : value;
            const tone = highlight && value > 0 ? (highlight === 'danger' ? 'tile-attention' : 'tile-info') : '';
            return (
              <Col key={key}>
                <Card className={`h-100 shadow-sm tile ${tone}`}>
                  <CardBody>
                    <Link to={to} className="stretched-link text-reset text-decoration-none">
                      <span className={`d-block fw-semibold ${money ? 'h4 my-2' : 'display-6'}`}>{shown}</span>
                      <span className="small">{label}</span>
                    </Link>
                  </CardBody>
                </Card>
              </Col>
            );
          })}
        </Row>
      )}
      <div className="d-flex flex-wrap gap-2 mt-4">
        <Link className="btn btn-success" to="/borrowed-books/new">
          Lend a book at the desk
        </Link>
        <Link className="btn btn-outline-secondary" to="/members/new">
          Register a walk-in member
        </Link>
      </div>
    </>
  );
}
