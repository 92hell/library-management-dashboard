import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, Form, Spinner } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert } from '../components/Feedback';
import Field from '../components/Field';
import ReadingRoom from '../components/ReadingRoom';
import { useAction, useFormFields } from '../hooks';
import { msg, useT } from '../i18n';

function validate(values) {
  const errors = {};
  if (values.newPassword.length < 10) errors.newPassword = msg('Use at least 10 characters.');
  else if (values.newPassword !== values.confirm) errors.confirm = msg('The two passwords are not the same.');
  return errors;
}

/**
 * Where the emailed link opens: choose a new password. The token is taken out of the address at
 * once, so it does not stay in the browser history or show on screen.
 */
export default function ResetPassword() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [token] = useState(() => params.get('token') ?? '');
  const [values, , handleChange] = useFormFields({ newPassword: '', confirm: '' });
  const [done, setDone] = useState(false);
  const submit = useAction();
  const t = useT();

  useEffect(() => {
    if (params.has('token')) navigate('/reset-password', { replace: true });
  }, [params, navigate]);

  async function handleSubmit(event) {
    event.preventDefault();
    if (await submit.run(() => api.post('/api/auth/password-reset/confirm', { token, newPassword: values.newPassword }),
      validate(values))) {
      setDone(true);
    }
  }

  const linkProblem = !token || submit.fieldErrors.token;
  return (
    <ReadingRoom title={t('Choose a new password')}>
      {done ? (
        <div role="status">
          <Alert color="success">{t('Your password was changed, and you were signed out everywhere else.')}</Alert>
          <Link to="/login" className="btn btn-primary w-100">{t('Sign in')}</Link>
        </div>
      ) : linkProblem ? (
        <>
          <Alert color="danger">{t(submit.fieldErrors.token ?? msg('This link is invalid or has expired. Ask for a new one.'))}</Alert>
          <Link to="/forgot-password" className="btn btn-primary w-100">{t('Ask for a new link')}</Link>
        </>
      ) : (
        <>
          <ErrorAlert error={Object.keys(submit.fieldErrors).length ? null : submit.error} onDismiss={submit.clearError} />
          <Form onSubmit={handleSubmit} noValidate>
            <Field label={t('New password')} name="newPassword" type="password" value={values.newPassword}
              onChange={handleChange} error={t(submit.fieldErrors.newPassword)} hint={t('At least 10 characters.')}
              autoComplete="new-password" autoFocus />
            <Field label={t('New password again')} name="confirm" type="password" value={values.confirm}
              onChange={handleChange} error={t(submit.fieldErrors.confirm)} autoComplete="new-password" />
            <Button color="primary" type="submit" className="w-100" disabled={submit.pending}>
              {submit.pending && <Spinner size="sm" className="me-2" />}
              {t('Save new password')}
            </Button>
          </Form>
        </>
      )}
    </ReadingRoom>
  );
}
