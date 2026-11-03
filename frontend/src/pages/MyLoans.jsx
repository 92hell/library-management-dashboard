import { Button, Table } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert, Loading } from '../components/Feedback';
import LoanStatus from '../components/LoanStatus';
import { formatDate } from '../format';
import { useAction, useApi } from '../hooks';

export default function MyLoans() {
  const loans = useApi('/api/my/loans');
  const action = useAction();

  async function cancel(loan) {
    if (!window.confirm(`Cancel your request for "${loan.book.title}"?`)) return;
    if (await action.run(() => api.post(`/api/my/loans/${loan.id}/cancel`))) loans.reload();
  }

  if (loans.error) return <ErrorAlert error={loans.error} />;
  if (!loans.data) return <Loading />;

  return (
    <>
      <h1 className="h3 mb-3">My loans</h1>
      <ErrorAlert error={action.error} onDismiss={action.clearError} />
      {loans.data.length === 0 ? (
        <p className="text-muted py-4">You have not borrowed or requested any books yet.</p>
      ) : (
        <Table responsive className="align-middle shadow-sm">
          <thead className="table-dark">
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
                  <span className="fw-semibold">{loan.book.title}</span>
                  <span className="d-block small text-muted">{loan.book.authorName}</span>
                </td>
                <td className="text-nowrap">{formatDate(loan.borrowDate)}</td>
                <td className="text-nowrap">{loan.status === 'ACTIVE' ? formatDate(loan.dueDate) : '—'}</td>
                <td>
                  <LoanStatus loan={loan} />
                </td>
                <td className="text-end">
                  {loan.status === 'PENDING' && (
                    <Button size="sm" color="secondary" outline onClick={() => cancel(loan)} disabled={action.pending}>
                      Cancel request
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </>
  );
}
