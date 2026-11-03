import { Alert, Spinner } from 'reactstrap';
import { useT } from '../i18n';

/** A spinner with a label; pass `label` already translated. */
export function Loading({ label }) {
  const t = useT();
  return (
    <div className="d-flex align-items-center gap-2 text-muted py-4" role="status">
      <Spinner size="sm" />
      <span>{label ?? t('Loading…')}</span>
    </div>
  );
}

/** Shows an ApiError (or any Error) message; renders nothing when there is no error. */
export function ErrorAlert({ error, onDismiss }) {
  if (!error) return null;
  return (
    <Alert color="danger" isOpen toggle={onDismiss}>
      {error.message}
    </Alert>
  );
}
