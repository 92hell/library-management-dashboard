import { Alert } from 'reactstrap';
import { formatMoney } from '../format';
import { useT } from '../i18n';

/** The customer's unpaid late fees, from /api/my/fines; nothing when they owe nothing. */
export default function FineBalance({ fines }) {
  const t = useT();
  if (!fines || fines.balance <= 0) return null;
  const owed = formatMoney(fines.balance, fines.currency);
  return (
    <Alert color={fines.blocked ? 'danger' : 'warning'}>
      {t('You owe {amount} in late fees.', { amount: owed })}{' '}
      {fines.blocked ? t('You can borrow again once it is paid at the desk.') : t('Please pay at the desk.')}
    </Alert>
  );
}
