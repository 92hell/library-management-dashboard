import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, Form, Spinner } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert, Loading } from '../components/Feedback';
import Field from '../components/Field';
import ReadingRoom from '../components/ReadingRoom';
import { useAction, useApi } from '../hooks';
import { msg, useT } from '../i18n';

/**
 * "Forgot password": asks for a link by username or email. The answer is the same whether or not
 * an account matches, so this page cannot be used to find out who has an account.
 */
export default function ForgotPassword() {
  const { data: options } = useApi('/api/auth/demo');
  const [identifier, setIdentifier] = useState('');
  const [sent, setSent] = useState(null);
  const submit = useAction();
  const t = useT();

  if (!options) return <Loading />;

  async function handleSubmit(event) {
    event.preventDefault();
    const value = identifier.trim();
    const errors = value ? {} : { identifier: msg('Enter your username or email address.') };
    let answer = null;
    if (await submit.run(async () => {
      answer = await api.post('/api/auth/password-reset', { identifier: value });
    }, errors)) {
      setSent(answer.message);
    }
  }

  return (
    <ReadingRoom title={t('Forgot your password?')}>
      {!options.passwordReset ? (
        <p>{t('Password reset by email is not available here. Ask a librarian to set a new password for you.')}</p>
      ) : sent ? (
        <div role="status">
          <Alert color="success">{sent}</Alert>
          <p className="small text-muted">{t('No email? Check your spam folder, or ask again in a few minutes.')}</p>
        </div>
      ) : (
        <>
          <p className="small text-muted">
            {t('Enter your username or the email address on your account. We will email you a link to choose a new password.')}
          </p>
          <ErrorAlert error={Object.keys(submit.fieldErrors).length ? null : submit.error} onDismiss={submit.clearError} />
          <Form onSubmit={handleSubmit} noValidate>
            <Field label={t('Username or email')} name="identifier" value={identifier}
              onChange={(e) => setIdentifier(e.target.value)} error={t(submit.fieldErrors.identifier)}
              autoComplete="username" autoFocus />
            <Button color="primary" type="submit" className="w-100" disabled={submit.pending}>
              {submit.pending && <Spinner size="sm" className="me-2" />}
              {t('Send me a link')}
            </Button>
          </Form>
        </>
      )}
      <p className="text-center small mt-3 mb-0">
        <Link to="/login">{t('Back to sign in')}</Link>
      </p>
    </ReadingRoom>
  );
}
