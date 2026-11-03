import { formatDate, formatMoney } from '../format';
import { msg, useT } from '../i18n';

const FEE_LINES = {
  UNPAID: msg('Late fee: {amount} (unpaid)'),
  PAID: msg('Late fee: {amount} (paid)'),
  WAIVED: msg('Late fee: {amount} (waived)'),
};

/** Loan status drawn as a library date-due stamp, with the late fee (so far) underneath when there is one. */
export default function LoanStatus({ loan }) {
  const { fine } = loan;
  const t = useT();
  if (!fine) return <Stamp loan={loan} />;
  const amount = formatMoney(fine.amount, fine.currency);
  return (
    <>
      <Stamp loan={loan} />
      <span className={`d-block small mt-1 ${fine.status === 'UNPAID' || fine.status === 'ACCRUING' ? 'text-danger' : 'text-muted'}`}>
        {fine.status === 'ACCRUING' ? t('Late fee so far: {amount}', { amount }) : t(FEE_LINES[fine.status], { amount })}
      </span>
    </>
  );
}

/** The stamp itself, including derived "Overdue" and the decline reason. */
function Stamp({ loan }) {
  const t = useT();
  switch (loan.status) {
    case 'PENDING':
      return <span className="stamp stamp-pending">{t('Requested')}</span>;
    case 'WAITLISTED':
      return <span className="stamp stamp-pending">{loan.queuePosition ? t('#{position} in queue', { position: loan.queuePosition }) : t('In queue')}</span>;
    case 'READY':
      return (
        <span>
          <span className="stamp stamp-returned">{t('Ready for pickup')}</span>
          {loan.readyUntil && <span className="small text-muted ms-2">{t('Collect by {date}', { date: formatDate(loan.readyUntil) })}</span>}
        </span>
      );
    case 'EXPIRED':
      return <span className="stamp stamp-cancelled">{t('Not collected')}</span>;
    case 'ACTIVE':
      return loan.overdue ? (
        <span className="stamp stamp-overdue">{t('Overdue')}</span>
      ) : (
        <span className="stamp stamp-active">{t('On loan')}</span>
      );
    case 'RETURNED':
      return <span className="stamp stamp-returned">{t('Returned {date}', { date: formatDate(loan.returnDate) })}</span>;
    case 'REJECTED':
      return (
        <span>
          <span className="stamp stamp-declined">{t('Declined')}</span>
          {loan.decisionNote && <span className="small text-muted ms-2">{t('Reason: {reason}', { reason: loan.decisionNote })}</span>}
        </span>
      );
    case 'CANCELLED':
      return <span className="stamp stamp-cancelled">{t('Cancelled')}</span>;
    default:
      return <span>{loan.status}</span>;
  }
}
