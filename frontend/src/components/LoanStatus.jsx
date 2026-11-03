import { formatDate } from '../format';

/** Loan status drawn as a library date-due stamp, including derived "Overdue" and the decline reason. */
export default function LoanStatus({ loan }) {
  switch (loan.status) {
    case 'PENDING':
      return <span className="stamp stamp-pending">Requested</span>;
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
