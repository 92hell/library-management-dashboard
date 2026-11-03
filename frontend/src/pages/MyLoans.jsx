import { Button, Table } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert, Loading } from '../components/Feedback';
import BookCover from '../components/BookCover';
import FineBalance from '../components/FineBalance';
import LoanStatus from '../components/LoanStatus';
import { formatDate, formatMoney } from '../format';
import { useAction, useApi } from '../hooks';

export default function MyLoans() {
  const loans = useApi('/api/my/loans');
  const fines = useApi('/api/my/fines');
  const action = useAction();

  async function cancel(loan) {
    const what = loan.status === 'READY' ? 'the copy set aside for you' : 'your request';
    if (!window.confirm(`Cancel ${what} for "${loan.book.title}"?`)) return;
    if (await action.run(() => api.post(`/api/my/loans/${loan.id}/cancel`))) loans.reload();
  }

  async function renew(loan) {
    if (await action.run(() => api.post(`/api/my/loans/${loan.id}/renew`))) loans.reload();
  }

  if (loans.error) return <ErrorAlert error={loans.error} />;
  if (!loans.data) return <Loading />;

  return (
    <>
      <h1 className="h3 mb-3 page-title">My loans</h1>
      <FineBalance fines={fines.data} />
      <ErrorAlert error={action.error} onDismiss={action.clearError} />
      {loans.data.length === 0 ? (
        <p className="empty-state">You have not borrowed or requested any books yet.</p>
      ) : (
        <Table responsive className="align-middle shadow-sm">
          <thead className="table-library">
            <tr>
              <th scope="col">Book</th>
              <th scope="col">Borrowed</th>
              <th scope="col">Due</th>
              <th scope="col">Status</th>
              <th scope="col" aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {loans.data.map((loan) => (
              <tr key={loan.id}>
                <td>
                  <div className="d-flex align-items-center gap-3">
                    <BookCover title={loan.book.title} />
                    <div>
                      <span className="fw-semibold">{loan.book.title}</span>
                      <span className="d-block small text-muted">{loan.book.authorName}</span>
                    </div>
                  </div>
                </td>
                <td className="text-nowrap">{formatDate(loan.borrowDate)}</td>
                <td className="text-nowrap">{loan.status === 'ACTIVE' ? formatDate(loan.dueDate) : '—'}</td>
                <td>
                  <LoanStatus loan={loan} />
                </td>
                <td className="text-end">
                  {loan.canRenew && (
                    <Button size="sm" color="primary" outline onClick={() => renew(loan)} disabled={action.pending}>
                      Renew
                    </Button>
                  )}
                  {['PENDING', 'WAITLISTED'].includes(loan.status) && (
                    <Button size="sm" color="secondary" outline onClick={() => cancel(loan)} disabled={action.pending}>
                      Cancel request
                    </Button>
                  )}
                  {loan.status === 'READY' && (
                    <Button size="sm" color="secondary" outline onClick={() => cancel(loan)} disabled={action.pending}>
                      Cancel hold
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {fines.data?.fines.length > 0 && <FeeHistory fines={fines.data.fines} />}
    </>
  );
}

const FEE_STATUS = { UNPAID: 'Unpaid', PAID: 'Paid', WAIVED: 'Waived' };

/** Every late fee the member was charged, settled or not. */
function FeeHistory({ fines }) {
  return (
    <section className="mt-4" aria-labelledby="late-fees-heading">
      <h2 id="late-fees-heading" className="h5">
        Late fees
      </h2>
      <Table responsive className="align-middle shadow-sm" aria-labelledby="late-fees-heading">
        <thead className="table-library">
          <tr>
            <th scope="col">Book</th>
            <th scope="col">Returned</th>
            <th scope="col">Late</th>
            <th scope="col">Fee</th>
            <th scope="col">Status</th>
          </tr>
        </thead>
        <tbody>
          {fines.map((fine) => (
            <tr key={fine.id}>
              <td className="fw-semibold">{fine.book.title}</td>
              <td className="text-nowrap">{formatDate(fine.returnDate)}</td>
              <td className="text-nowrap">
                {fine.daysLate} {fine.daysLate === 1 ? 'day' : 'days'} late
              </td>
              <td className="text-nowrap">{formatMoney(fine.amount, fine.currency)}</td>
              <td>
                {FEE_STATUS[fine.status]}
                {fine.status === 'WAIVED' && fine.note && <span className="d-block small text-muted">{fine.note}</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
    </section>
  );
}
