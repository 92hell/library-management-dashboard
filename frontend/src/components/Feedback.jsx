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

/**
 * An error's message in the language of `t`. The app's own messages (msg keys, see ApiError) are
 * translated; messages from the server have no translation, so t() leaves them as they are.
 */
export function errorText(error, t) {
  return t(error.text ?? error.message, error.vars);
}

/** Shows an ApiError (or any Error) message; renders nothing when there is no error. */
export function ErrorAlert({ error, onDismiss }) {
  const t = useT();
  if (!error) return null;
  return (
    <Alert color="danger" isOpen toggle={onDismiss}>
      {errorText(error, t)}
    </Alert>
  );
}
