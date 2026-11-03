import { Badge } from 'reactstrap';
import { formatDate } from '../format';

/** Status badge for a loan, including the derived "Overdue" state and the rejection reason. */
export default function LoanStatus({ loan }) {
  switch (loan.status) {
    case 'PENDING':
      return <Badge color="secondary">Requested</Badge>;
    case 'ACTIVE':
      return loan.overdue ? (
        <Badge color="danger">Overdue</Badge>
      ) : (
        <Badge color="warning" className="text-dark">
          On loan
        </Badge>
      );
    case 'RETURNED':
      return <span>Returned {formatDate(loan.returnDate)}</span>;
    case 'REJECTED':
      return (
        <span>
          <Badge color="dark">Declined</Badge>
          {loan.decisionNote && <span className="small text-muted ms-2">Reason: {loan.decisionNote}</span>}
        </span>
      );
    case 'CANCELLED':
      return <Badge color="light" className="text-dark border">Cancelled</Badge>;
    default:
      return <span>{loan.status}</span>;
  }
}
