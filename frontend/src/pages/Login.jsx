import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { Alert, Button, Card, CardBody, Container, Form, Spinner } from 'reactstrap';
import { useAuth } from '../auth';
import Bookshelf from '../components/Bookshelf';
import BrandMark from '../components/BrandMark';
import Field from '../components/Field';
import { errorText, Loading } from '../components/Feedback';
import LanguageSwitch from '../components/LanguageSwitch';
import { useApi, useFormFields } from '../hooks';
import { msg, useT } from '../i18n';

/** Demo account labels as the server sends them, with their button texts. */
const DEMO_BUTTONS = { Customer: msg('Try as customer'), Librarian: msg('Try as librarian') };

export default function Login() {
  const { user, loading, login } = useAuth();
  const location = useLocation();
  const { data: demo } = useApi('/api/auth/demo');
  const [credentials, setCredentials, handleChange] = useFormFields({ username: '', password: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const t = useT();

  if (loading) return <Loading label={t('Checking your session…')} />;
  // Back to the page that needed a sign-in, with its query string (e.g. the catalogue's filters).
  const from = location.state?.from;
  if (user) return <Navigate to={from ? `${from.pathname}${from.search ?? ''}` : '/'} replace />;

  async function signIn(username, password) {
    setSubmitting(true);
    setError(null);
    try {
      await login(username, password);
    } catch (err) {
      // Our own messages are stored untranslated (msg) so they follow a language switch; server messages stay as they are.
      setError(err.status === 401 ? { message: msg('Invalid username or password.') } : err);
    } finally {
      setSubmitting(false);
    }
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (!credentials.username.trim() || !credentials.password) {
      setError({ message: msg('Please enter your username and password.') });
      return;
    }
    signIn(credentials.username.trim(), credentials.password);
  }

  function signInWithDemo(account) {
    setCredentials({ username: account.username, password: account.password });
    signIn(account.username, account.password);
  }

  return (
    <div className="reading-room">
      <Container className="py-5" style={{ maxWidth: '26rem' }}>
        <Bookshelf />
        <h1 className="h3 mb-1 text-center reading-room-title d-flex align-items-center justify-content-center gap-2">
          <BrandMark size={34} /> {t('Library')}
        </h1>
        <p className="text-center page-subtitle mb-4">{t('Welcome back to the reading room.')}</p>
        <Card className="shadow-sm">
          <CardBody className="p-4">
            <div className="d-flex justify-content-between align-items-center gap-2 mb-3">
              <h2 className="h5 mb-0">{t('Sign in')}</h2>
              <LanguageSwitch />
            </div>
            {error && <Alert color="danger">{errorText(error, t)}</Alert>}
            <Form onSubmit={handleSubmit} noValidate>
              <Field
                label={t('Username')}
                name="username"
                value={credentials.username}
                onChange={handleChange}
                autoComplete="username"
                autoFocus
              />
              <Field
                label={t('Password')}
                name="password"
                type="password"
                value={credentials.password}
                onChange={handleChange}
                autoComplete="current-password"
              />
              <Button color="primary" type="submit" className="w-100" disabled={submitting}>
                {submitting && <Spinner size="sm" className="me-2" />}
                {t('Sign in')}
              </Button>
            </Form>
            {demo?.passwordReset && (
              <p className="text-center small mt-3 mb-0">
                <Link to="/forgot-password">{t('Forgot your password?')}</Link>
              </p>
            )}
            {demo?.registrationOpen !== false && (
              <p className="text-center small mt-3 mb-0">
                {t('New here?')} <Link to="/register">{t('Create an account')}</Link>
              </p>
            )}
            {demo?.enabled && demo.accounts.length > 0 && (
              <>
                <hr />
                <p className="small text-muted mb-2">{t('This is a demo. Try it without signing up:')}</p>
                <div className="d-grid gap-2">
                  {demo.accounts.map((account) => (
                    <Button
                      key={account.username}
                      color="secondary"
                      outline
                      onClick={() => signInWithDemo(account)}
                      disabled={submitting}
                    >
                      {DEMO_BUTTONS[account.label]
                        ? t(DEMO_BUTTONS[account.label])
                        : t('Try as {account}', { account: account.label.toLowerCase() })}
                    </Button>
                  ))}
                </div>
              </>
            )}
          </CardBody>
        </Card>
      </Container>
    </div>
  );
}
