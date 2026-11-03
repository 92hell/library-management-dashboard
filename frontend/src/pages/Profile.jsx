import { useState } from 'react';
import { Alert, Button, Card, CardBody, CardTitle, Col, Form, Row } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import Field from '../components/Field';
import { formatDate } from '../format';
import { useAction, useApi, useFormFields } from '../hooks';

export default function Profile() {
  const profile = useApi('/api/profile');
  if (profile.error) return <ErrorAlert error={profile.error} />;
  if (!profile.data) return <Loading />;
  return <ProfileView key={profile.data.id} profile={profile.data} onSaved={profile.reload} />;
}

function ProfileView({ profile, onSaved }) {
  const { isCustomer } = useAuth();
  const card = profile.member;

  return (
    <>
      <h1 className="h3 mb-3">My profile</h1>
      <Row className="g-4">
        <Col lg={6}>
          <DetailsForm profile={profile} showPhone={isCustomer} onSaved={onSaved} />
        </Col>
        <Col lg={6}>
          {card && (
            <Card className="shadow-sm mb-4">
              <CardBody>
                <CardTitle tag="h2" className="h5">
                  Library card
                </CardTitle>
                <p className="display-6 font-monospace mb-1">{card.libraryCardId}</p>
                <p className="small text-muted mb-0">
                  Show this number at the desk. Member since your registration · born {formatDate(card.dateOfBirth)}
                </p>
              </CardBody>
            </Card>
          )}
          <PasswordForm />
        </Col>
      </Row>
    </>
  );
}

function DetailsForm({ profile, showPhone, onSaved }) {
  const [values, , handleChange] = useFormFields({
    displayName: profile.displayName,
    email: profile.email ?? '',
    phone: profile.member?.phone ?? '',
  });
  const save = useAction();
  const [saved, setSaved] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setSaved(false);
    const body = { displayName: values.displayName.trim(), email: values.email.trim(), phone: showPhone ? values.phone.trim() : null };
    if (await save.run(() => api.put('/api/profile', body))) {
      setSaved(true);
      onSaved();
    }
  }

  return (
    <Form onSubmit={handleSubmit} noValidate className="p-4 border rounded shadow-sm">
      <h2 className="h5 mb-3">Account details</h2>
      <p className="small text-muted">
        Signed in as <strong>{profile.username}</strong> ({profile.role === 'LIBRARIAN' ? 'librarian' : 'member'})
      </p>
      {saved && <Alert color="success">Your details were saved.</Alert>}
      <ErrorAlert error={Object.keys(save.fieldErrors).length ? null : save.error} onDismiss={save.clearError} />
      <Field label="Name" name="displayName" value={values.displayName} onChange={handleChange} error={save.fieldErrors.displayName} />
      <Field label="Email" name="email" type="email" value={values.email} onChange={handleChange} error={save.fieldErrors.email} />
      {showPhone && <Field label="Phone" name="phone" type="tel" value={values.phone} onChange={handleChange} error={save.fieldErrors.phone} />}
      <Button color="primary" type="submit" disabled={save.pending}>
        Save details
      </Button>
    </Form>
  );
}

function PasswordForm() {
  const [values, setValues, handleChange] = useFormFields({ currentPassword: '', newPassword: '' });
  const change = useAction();
  const [changed, setChanged] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setChanged(false);
    const clientErrors = values.newPassword.length < 10 ? { newPassword: 'Use at least 10 characters.' } : {};
    if (await change.run(() => api.put('/api/profile/password', values), clientErrors)) {
      setChanged(true);
      setValues({ currentPassword: '', newPassword: '' });
    }
  }

  return (
    <Form onSubmit={handleSubmit} noValidate className="p-4 border rounded shadow-sm">
      <h2 className="h5 mb-3">Change password</h2>
      {changed && <Alert color="success">Your password was changed.</Alert>}
      <Field
        label="Current password"
        name="currentPassword"
        type="password"
        value={values.currentPassword}
        onChange={handleChange}
        error={change.fieldErrors.currentPassword}
        autoComplete="current-password"
      />
      <Field
        label="New password"
        name="newPassword"
        type="password"
        value={values.newPassword}
        onChange={handleChange}
        error={change.fieldErrors.newPassword}
        autoComplete="new-password"
      />
      <Button color="primary" type="submit" disabled={change.pending}>
        Change password
      </Button>
    </Form>
  );
}
