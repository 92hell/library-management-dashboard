import { Link, Navigate, useNavigate } from 'react-router-dom';
import { Button, Card, CardBody, Col, Container, Form, FormGroup, Input, Label, Row, Spinner } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import Bookshelf from '../components/Bookshelf';
import Field from '../components/Field';
import LanguageSwitch from '../components/LanguageSwitch';
import { useAction, useApi, useFormFields } from '../hooks';
import { msg, useT } from '../i18n';

const EMPTY = {
  name: '', email: '', phone: '', dateOfBirth: '', username: '', password: '', hasCard: false, libraryCardId: '',
};

const REQUIRED = {
  name: msg('Name is required.'),
  email: msg('Email is required.'),
  phone: msg('Phone is required.'),
  dateOfBirth: msg('Date of birth is required.'),
  username: msg('Username is required.'),
};

function validate(values, t) {
  const errors = {};
  for (const [field, message] of Object.entries(REQUIRED)) {
    if (!values[field].trim()) errors[field] = t(message);
  }
  if (values.password.length < 10) errors.password = t('Use at least 10 characters.');
  if (values.hasCard && !values.libraryCardId.trim()) errors.libraryCardId = t('Enter the ID printed on your library card.');
  return errors;
}

/** Puts `element` where `{name}` is in an already translated sentence, so the sentence can be reordered freely. */
function withElement(text, name, element) {
  const [before, after = ''] = text.split(`{${name}}`);
  return (
    <>
      {before}
      {element}
      {after}
    </>
  );
}

/**
 * Customer sign-up. Walk-in members who already have a library card enter its ID; the library
 * links the new account to that card (and its loan history) when the email or phone matches.
 */
export default function Register() {
  const { user, loading, login } = useAuth();
  const navigate = useNavigate();
  const [values, , handleChange] = useFormFields(EMPTY);
  const submit = useAction();
  const { data: demo } = useApi('/api/auth/demo');
  const { fieldErrors } = submit;
  const t = useT();

  if (loading) return <Loading label={t('Checking your session…')} />;
  if (user) return <Navigate to="/" replace />;
  if (demo?.registrationOpen === false) {
    return (
      <div className="reading-room">
        <Container className="py-5" style={{ maxWidth: '30rem' }}>
          <Bookshelf />
          <div className="d-flex justify-content-end mb-2">
            <LanguageSwitch />
          </div>
          <h1 className="h3 mb-3 text-center reading-room-title">{t('Registration is turned off in this demo')}</h1>
          <p className="text-center">
            {t('So that no real personal data is collected, this demo has no sign-up.')}{' '}
            {withElement(
              t('Use one of the demo accounts on the {link} instead.'),
              'link',
              <Link to="/login">{t('sign-in page')}</Link>,
            )}
          </p>
        </Container>
      </div>
    );
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const body = {
      username: values.username.trim(),
      password: values.password,
      name: values.name.trim(),
      email: values.email.trim(),
      phone: values.phone.trim(),
      dateOfBirth: values.dateOfBirth,
      libraryCardId: values.hasCard ? values.libraryCardId.trim() : null,
    };
    const ok = await submit.run(async () => {
      await api.post('/api/auth/register', body);
      await login(body.username, body.password);
    }, validate(values, t));
    if (ok) navigate('/', { replace: true });
  }

  return (
    <div className="reading-room">
      <Container className="py-5" style={{ maxWidth: '36rem' }}>
        <Bookshelf />
        <h1 className="h3 mb-1 text-center reading-room-title">{t('Get your library card')}</h1>
        <p className="text-center page-subtitle mb-4">{t('Already have a card from the desk? Link it below.')}</p>
        <Card className="shadow-sm">
          <CardBody className="p-4">
            <div className="d-flex justify-content-end mb-2">
              <LanguageSwitch />
            </div>
            <ErrorAlert error={Object.keys(fieldErrors).length ? null : submit.error} onDismiss={submit.clearError} />
            <Form onSubmit={handleSubmit} noValidate>
              <Field label={t('Full name')} name="name" value={values.name} onChange={handleChange} error={fieldErrors.name} autoComplete="name" />
              <Row>
                <Col md={7}>
                  <Field label={t('Email')} name="email" type="email" value={values.email} onChange={handleChange} error={fieldErrors.email} autoComplete="email" />
                </Col>
                <Col md={5}>
                  <Field label={t('Phone')} name="phone" type="tel" value={values.phone} onChange={handleChange} error={fieldErrors.phone} autoComplete="tel" />
                </Col>
              </Row>
              <Field label={t('Date of birth')} name="dateOfBirth" type="date" value={values.dateOfBirth} onChange={handleChange} error={fieldErrors.dateOfBirth} />
              <Row>
                <Col md={6}>
                  <Field label={t('Username')} name="username" value={values.username} onChange={handleChange} error={fieldErrors.username} autoComplete="username" />
                </Col>
                <Col md={6}>
                  <Field label={t('Password')} name="password" type="password" value={values.password} onChange={handleChange} error={fieldErrors.password} autoComplete="new-password" />
                </Col>
              </Row>
              <FormGroup check className="mb-3">
                <Input id="hasCard" name="hasCard" type="checkbox" checked={values.hasCard} onChange={handleChange} />
                <Label for="hasCard" check>
                  {t('I already have a library card (link it to this account)')}
                </Label>
              </FormGroup>
              {values.hasCard && (
                <Field
                  label={t('Library card ID')}
                  name="libraryCardId"
                  placeholder={t('e.g. {example}', { example: 'LIB-7KQ2XM' })}
                  hint={t('We match it with the email or phone number the library has on file.')}
                  value={values.libraryCardId}
                  onChange={handleChange}
                  error={fieldErrors.libraryCardId}
                />
              )}
              <Button color="primary" type="submit" className="w-100" disabled={submit.pending}>
                {submit.pending && <Spinner size="sm" className="me-2" />}
                {t('Create account')}
              </Button>
            </Form>
            <p className="text-center small mt-3 mb-0">
              {t('Already registered?')} <Link to="/login">{t('Sign in')}</Link>
            </p>
          </CardBody>
        </Card>
      </Container>
    </div>
  );
}
