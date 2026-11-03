import { Link } from 'react-router-dom';
import { Button, ButtonGroup } from 'reactstrap';
import { useAuth } from '../auth';
import { useT } from '../i18n';

/**
 * Edit/Delete buttons for librarians (plus any extra actions passed as children);
 * a read-only "View" link for everyone else.
 */
export default function RowActions({ to, onDelete, disabled, children }) {
  const { isLibrarian } = useAuth();
  const t = useT();

  if (!isLibrarian) {
    return (
      <Button size="sm" color="secondary" outline tag={Link} to={to}>
        {t('View')}
      </Button>
    );
  }

  return (
    <ButtonGroup size="sm">
      {children}
      <Button color="primary" tag={Link} to={to}>
        {t('Edit')}
      </Button>
      <Button color="danger" onClick={onDelete} disabled={disabled}>
        {t('Delete')}
      </Button>
    </ButtonGroup>
  );
}
