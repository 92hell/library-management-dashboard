import { Link } from 'react-router-dom';
import { Button, Spinner } from 'reactstrap';
import { useT } from '../i18n';

/** Save + Cancel for editable forms, a single Back link for read-only ones. */
export default function FormActions({ readOnly, saving, backTo }) {
  const t = useT();
  if (readOnly) {
    return (
      <Button color="secondary" tag={Link} to={backTo}>
        {t('Back')}
      </Button>
    );
  }
  return (
    <div className="d-flex gap-2">
      <Button color="primary" type="submit" disabled={saving}>
        {saving && <Spinner size="sm" className="me-2" />}
        {t('Save')}
      </Button>
      <Button color="secondary" outline tag={Link} to={backTo}>
        {t('Cancel')}
      </Button>
    </div>
  );
}
