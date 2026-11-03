import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { Alert, Button, Card, CardBody, Container, Form, Spinner } from 'reactstrap';
import { useAuth } from '../auth';
import Bookshelf from '../components/Bookshelf';
import BrandMark from '../components/BrandMark';
import Field from '../components/Field';
import { Loading } from '../components/Feedback';
import { useApi, useFormFields } from '../hooks';

export default function Login() {
  const { user, loading, login } = useAuth();
  const location = useLocation();
  const { data: demo } = useApi('/api/auth/demo');
  const [credentials, setCredentials, handleChange] = useFormFields({ username: '', password: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (loading) return <Loading label="Checking your session…" />;
  if (user) return <Navigate to={location.state?.from?.pathname ?? '/'} replace />;

  async function signIn(username, password) {
    setSubmitting(true);
    setError(null);
    try {
      await login(username, password);
    } catch (err) {
      setError(err.status === 401 ? 'Invalid username or password.' : err.message);
    } finally {
      setSubmitting(false);
    }
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (!credentials.username.trim() || !credentials.password) {
      setError('Please enter your username and password.');
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
          <BrandMark size={34} /> Library
        </h1>
        <p className="text-center page-subtitle mb-4">Welcome back to the reading room.</p>
        <Card className="shadow-sm">
          <CardBody className="p-4">
            <h2 className="h5 mb-3">Sign in</h2>
            {error && <Alert color="danger">{error}</Alert>}
            <Form onSubmit={handleSubmit} noValidate>
              <Field
                label="Username"
                name="username"
                value={credentials.username}
                onChange={handleChange}
                autoComplete="username"
                autoFocus
              />
              <Field
                label="Password"
                name="password"
                type="password"
                value={credentials.password}
                onChange={handleChange}
                autoComplete="current-password"
              />
              <Button color="primary" type="submit" className="w-100" disabled={submitting}>
                {submitting && <Spinner size="sm" className="me-2" />}
                Sign in
              </Button>
            </Form>
            <p className="text-center small mt-3 mb-0">
              New here? <Link to="/register">Create an account</Link>
            </p>
            {demo?.enabled && demo.accounts.length > 0 && (
              <>
                <hr />
                <p className="small text-muted mb-2">This is a demo. Try it without signing up:</p>
                <div className="d-grid gap-2">
                  {demo.accounts.map((account) => (
                    <Button
                      key={account.username}
                      color="secondary"
                      outline
                      onClick={() => signInWithDemo(account)}
                      disabled={submitting}
                    >
                      Try as {account.label.toLowerCase()}
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
