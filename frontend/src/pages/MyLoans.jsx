import { Button, Table } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert, Loading } from '../components/Feedback';
import BookCover from '../components/BookCover';
import FineBalance from '../components/FineBalance';
import LoanStatus from '../components/LoanStatus';
import { formatDate, formatMoney } from '../format';
import { useAction, useApi } from '../hooks';
import { msg, useT } from '../i18n';

export default function MyLoans() {
  const loans = useApi('/api/my/loans');
  const fines = useApi('/api/my/fines');
  const action = useAction();
  const t = useT();

  async function cancel(loan) {
    const title = loan.book.title;
    const question =
      loan.status === 'READY'
        ? t('Cancel the copy set aside for you for "{title}"?', { title })
        : t('Cancel your request for "{title}"?', { title });
    if (!window.confirm(question)) return;
    if (await action.run(() => api.post(`/api/my/loans/${loan.id}/cancel`))) loans.reload();
  }

  async function renew(loan) {
    if (await action.run(() => api.post(`/api/my/loans/${loan.id}/renew`))) loans.reload();
  }

  if (loans.error) return <ErrorAlert error={loans.error} />;
  if (!loans.data) return <Loading />;

  return (
    <>
      <h1 className="h3 mb-3 page-title">{t('My loans')}</h1>
      <FineBalance fines={fines.data} />
      <ErrorAlert error={action.error} onDismiss={action.clearError} />
      {loans.data.length === 0 ? (
        <p className="empty-state">{t('You have not borrowed or requested any books yet.')}</p>
      ) : (
        <Table responsive className="align-middle shadow-sm">
          <thead className="table-library">
            <tr>
              <th scope="col">{t('Book')}</th>
              <th scope="col">{t('Borrowed')}</th>
              <th scope="col">{t('Due')}</th>
              <th scope="col">{t('Status')}</th>
              <th scope="col" aria-label={t('Actions')} />
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
                      {t('Renew')}
                    </Button>
                  )}
                  {['PENDING', 'WAITLISTED'].includes(loan.status) && (
                    <Button size="sm" color="secondary" outline onClick={() => cancel(loan)} disabled={action.pending}>
                      {t('Cancel request')}
                    </Button>
                  )}
                  {loan.status === 'READY' && (
                    <Button size="sm" color="secondary" outline onClick={() => cancel(loan)} disabled={action.pending}>
                      {t('Cancel hold')}
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

const FEE_STATUS = { UNPAID: msg('Unpaid'), PAID: msg('Paid'), WAIVED: msg('Waived') };

/** Every late fee the member was charged, settled or not. */
function FeeHistory({ fines }) {
  const t = useT();
  return (
    <section className="mt-4" aria-labelledby="late-fees-heading">
      <h2 id="late-fees-heading" className="h5">
        {t('Late fees')}
      </h2>
      <Table responsive className="align-middle shadow-sm" aria-labelledby="late-fees-heading">
        <thead className="table-library">
          <tr>
            <th scope="col">{t('Book')}</th>
            <th scope="col">{t('Returned')}</th>
            <th scope="col">{t('Late')}</th>
            <th scope="col">{t('Fee')}</th>
            <th scope="col">{t('Status')}</th>
          </tr>
        </thead>
        <tbody>
          {fines.map((fine) => (
            <tr key={fine.id}>
              <td className="fw-semibold">{fine.book.title}</td>
              <td className="text-nowrap">{formatDate(fine.returnDate)}</td>
              <td className="text-nowrap">
                {fine.daysLate === 1 ? t('{n} day late', { n: fine.daysLate }) : t('{n} days late', { n: fine.daysLate })}
              </td>
              <td className="text-nowrap">{formatMoney(fine.amount, fine.currency)}</td>
              <td>
                {FEE_STATUS[fine.status] && t(FEE_STATUS[fine.status])}
                {fine.status === 'WAIVED' && fine.note && <span className="d-block small text-muted">{fine.note}</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
    </section>
  );
}
