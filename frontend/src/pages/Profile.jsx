import { useState } from 'react';
import { Alert, Button, Col, Form, Row } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import Field from '../components/Field';
import { CardValidity } from '../components/MemberCardPanel';
import { formatDate, todayIso } from '../format';
import { useAction, useApi, useFormFields } from '../hooks';
import { msg, useT } from '../i18n';

export default function Profile() {
  const profile = useApi('/api/profile');
  if (profile.error) return <ErrorAlert error={profile.error} />;
  if (!profile.data) return <Loading />;
  return <ProfileView key={profile.data.id} profile={profile.data} onSaved={profile.reload} />;
}

function ProfileView({ profile, onSaved }) {
  const { user, isCustomer } = useAuth();
  const card = profile.member;
  const readOnly = Boolean(user?.demoAccount);
  const t = useT();

  return (
    <>
      <h1 className="h3 mb-3 page-title">{t('My profile')}</h1>
      <Row className="g-4">
        <Col lg={6}>
          {readOnly ? (
            <div className="p-4 border rounded shadow-sm">
              <h2 className="h5 mb-3">{t('Account details')}</h2>
              <p className="mb-1">
                <strong>{profile.displayName}</strong> · {profile.email}
              </p>
              <p className="small text-muted mb-0">
                {t('This is a shared demo account, so its details and password can\'t be changed.')}
              </p>
            </div>
          ) : (
            <DetailsForm profile={profile} showPhone={isCustomer} onSaved={onSaved} />
          )}
        </Col>
        <Col lg={6}>
          {card && (
            <section className="library-card mb-4" aria-label={t('Library card')}>
              <div className="library-card-header">
                <span>{t('Library card')}</span>
                <span aria-hidden="true">📚</span>
              </div>
              <div className="library-card-body">
                <p className="library-card-id mb-1">{card.libraryCardId}</p>
                <p className="mb-1 fw-semibold">{card.name}</p>
                <p className="small text-muted mb-1">
                  {t('Born {date}', { date: formatDate(card.dateOfBirth) })} ·{' '}
                  {t('Show this card ID at the desk to borrow in person.')}
                </p>
                <p className="small mb-0">
                  <CardValidity member={card} />
                </p>
              </div>
            </section>
          )}
          {card && <CardExpiryNotice card={card} />}
          {!readOnly && <PasswordForm />}
        </Col>
      </Row>
    </>
  );
}

/** Days a member is warned before their card expires. */
const EXPIRY_WARNING_DAYS = 30;

function daysUntil(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const [ty, tm, td] = todayIso().split('-').map(Number);
  return Math.round((Date.UTC(y, m - 1, d) - Date.UTC(ty, tm - 1, td)) / 86400000);
}

/** Tells the member to renew at the desk when their card has expired or expires within 30 days. */
function CardExpiryNotice({ card }) {
  const t = useT();
  if (!card.cardExpiresOn) return null;
  if (card.cardExpired) {
    return (
      <Alert color="danger">
        {t('Your library card expired on {date}. Renew it at the desk to borrow again.', { date: formatDate(card.cardExpiresOn) })}
      </Alert>
    );
  }
  if (daysUntil(card.cardExpiresOn) > EXPIRY_WARNING_DAYS) return null;
  return (
    <Alert color="warning">
      {t('Your library card expires on {date}. Renew it at the desk to keep borrowing.', { date: formatDate(card.cardExpiresOn) })}
    </Alert>
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
  const t = useT();
  // Split around {username} so it can be shown in bold wherever the language puts it.
  const [signedInBefore, signedInAfter = ''] = (
    profile.role === 'LIBRARIAN' ? t('Signed in as {username} (librarian)') : t('Signed in as {username} (member)')
  ).split('{username}');

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
      <h2 className="h5 mb-3">{t('Account details')}</h2>
      <p className="small text-muted">
        {signedInBefore}
        <strong>{profile.username}</strong>
        {signedInAfter}
      </p>
      {saved && <Alert color="success">{t('Your details were saved.')}</Alert>}
      <ErrorAlert error={Object.keys(save.fieldErrors).length ? null : save.error} onDismiss={save.clearError} />
      <Field label={t('Name')} name="displayName" value={values.displayName} onChange={handleChange} error={t(save.fieldErrors.displayName)} />
      <Field label={t('Email')} name="email" type="email" value={values.email} onChange={handleChange} error={t(save.fieldErrors.email)} />
      {showPhone && <Field label={t('Phone')} name="phone" type="tel" value={values.phone} onChange={handleChange} error={t(save.fieldErrors.phone)} />}
      <Button color="primary" type="submit" disabled={save.pending}>
        {t('Save details')}
      </Button>
    </Form>
  );
}

function PasswordForm() {
  const [values, setValues, handleChange] = useFormFields({ currentPassword: '', newPassword: '' });
  const change = useAction();
  const [changed, setChanged] = useState(false);
  const t = useT();

  async function handleSubmit(event) {
    event.preventDefault();
    setChanged(false);
    // Stored untranslated and translated where shown, so the message follows a language switch.
    const clientErrors = values.newPassword.length < 10 ? { newPassword: msg('Use at least 10 characters.') } : {};
    if (await change.run(() => api.put('/api/profile/password', values), clientErrors)) {
      setChanged(true);
      setValues({ currentPassword: '', newPassword: '' });
    }
  }

  return (
    <Form onSubmit={handleSubmit} noValidate className="p-4 border rounded shadow-sm">
      <h2 className="h5 mb-3">{t('Change password')}</h2>
      {changed && <Alert color="success">{t('Your password was changed.')}</Alert>}
      <Field
        label={t('Current password')}
        name="currentPassword"
        type="password"
        value={values.currentPassword}
        onChange={handleChange}
        error={t(change.fieldErrors.currentPassword)}
        autoComplete="current-password"
      />
      <Field
        label={t('New password')}
        name="newPassword"
        type="password"
        value={values.newPassword}
        onChange={handleChange}
        error={t(change.fieldErrors.newPassword)}
        autoComplete="new-password"
      />
      <Button color="primary" type="submit" disabled={change.pending}>
        {t('Change password')}
      </Button>
    </Form>
  );
}
