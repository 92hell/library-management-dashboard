import { formatDate, formatMoney } from '../format';

const FEE_STATES = { UNPAID: 'unpaid', PAID: 'paid', WAIVED: 'waived' };

/** Loan status drawn as a library date-due stamp, with the late fee (so far) underneath when there is one. */
export default function LoanStatus({ loan }) {
  const { fine } = loan;
  if (!fine) return <Stamp loan={loan} />;
  const amount = formatMoney(fine.amount, fine.currency);
  return (
    <>
      <Stamp loan={loan} />
      <span className={`d-block small mt-1 ${fine.status === 'UNPAID' || fine.status === 'ACCRUING' ? 'text-danger' : 'text-muted'}`}>
        {fine.status === 'ACCRUING' ? `Late fee so far: ${amount}` : `Late fee: ${amount} (${FEE_STATES[fine.status]})`}
      </span>
    </>
  );
}

/** The stamp itself, including derived "Overdue" and the decline reason. */
function Stamp({ loan }) {
  switch (loan.status) {
    case 'PENDING':
      return <span className="stamp stamp-pending">Requested</span>;
    case 'WAITLISTED':
      return <span className="stamp stamp-pending">{loan.queuePosition ? `#${loan.queuePosition} in queue` : 'In queue'}</span>;
    case 'READY':
      return (
        <span>
          <span className="stamp stamp-returned">Ready for pickup</span>
          {loan.readyUntil && <span className="small text-muted ms-2">Collect by {formatDate(loan.readyUntil)}</span>}
        </span>
      );
    case 'EXPIRED':
      return <span className="stamp stamp-cancelled">Not collected</span>;
    case 'ACTIVE':
      return loan.overdue ? (
        <span className="stamp stamp-overdue">Overdue</span>
      ) : (
        <span className="stamp stamp-active">On loan</span>
      );
    case 'RETURNED':
      return <span className="stamp stamp-returned">Returned {formatDate(loan.returnDate)}</span>;
    case 'REJECTED':
      return (
        <span>
          <span className="stamp stamp-declined">Declined</span>
          {loan.decisionNote && <span className="small text-muted ms-2">Reason: {loan.decisionNote}</span>}
        </span>
      );
    case 'CANCELLED':
      return <span className="stamp stamp-cancelled">Cancelled</span>;
    default:
      return <span>{loan.status}</span>;
  }
}
