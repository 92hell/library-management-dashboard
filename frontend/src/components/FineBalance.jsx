import { Alert } from 'reactstrap';
import { formatMoney } from '../format';

/** The customer's unpaid late fees, from /api/my/fines; nothing when they owe nothing. */
export default function FineBalance({ fines }) {
  if (!fines || fines.balance <= 0) return null;
  const owed = formatMoney(fines.balance, fines.currency);
  return (
    <Alert color={fines.blocked ? 'danger' : 'warning'}>
      You owe {owed} in late fees.{' '}
      {fines.blocked ? 'You can borrow again once it is paid at the desk.' : 'Please pay at the desk.'}
    </Alert>
  );
}
