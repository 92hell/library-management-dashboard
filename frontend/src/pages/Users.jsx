import { useState } from 'react';
import { Badge, Button, Col, Collapse, Form, Input, Label, Row } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert } from '../components/Feedback';
import Field from '../components/Field';
import PagedTable from '../components/PagedTable';
import SearchBox from '../components/SearchBox';
import { useAction, useDebouncedValue, useFormFields, usePagedList } from '../hooks';
import { msg, useT } from '../i18n';

const EMPTY_LIBRARIAN = { username: '', displayName: '', email: '', password: '' };

/** Online accounts: librarians and customers. Librarians can add staff and disable accounts. */
export default function Users() {
  const { user } = useAuth();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const q = useDebouncedValue(search.trim());
  const users = usePagedList('/api/users', { q, role });
  const toggle = useAction();
  const [adding, setAdding] = useState(false);
  const t = useT();

  async function setEnabled(account, enabled) {
    const vars = { username: account.username };
    const question = enabled ? t('Enable the account "{username}"?', vars) : t('Disable the account "{username}"?', vars);
    if (!window.confirm(question)) return;
    if (await toggle.run(() => api.put(`/api/users/${account.id}/enabled`, { enabled }))) users.reload();
  }

  return (
    <>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 mb-0">{t('Users')}</h1>
        <Button color="success" onClick={() => setAdding((open) => !open)} aria-expanded={adding}>
          {t('Add librarian')}
        </Button>
      </div>
      <Collapse isOpen={adding}>
        <NewLibrarianForm onCreated={() => { setAdding(false); users.reload(); }} />
      </Collapse>
      <Row className="g-2 mb-3">
        <Col md={8}>
          <SearchBox
            id="user-search"
            label={t('Search users')}
            placeholder={t('Search by username, name or email')}
            value={search}
            onChange={setSearch}
          />
        </Col>
        <Col md={4}>
          <Label for="user-role" className="visually-hidden">{t('Role')}</Label>
          <Input id="user-role" type="select" value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="">{t('All roles')}</option>
            <option value="LIBRARIAN">{t('Librarians')}</option>
            <option value="CUSTOMER">{t('Members')}</option>
          </Input>
        </Col>
      </Row>
      <ErrorAlert error={toggle.error} onDismiss={toggle.clearError} />
      <PagedTable
        list={users}
        headers={[t('Username'), t('Name'), t('Email'), t('Role'), t('Status'), '']}
        emptyMessage={t('No accounts found.')}
        renderRow={(account) => (
          <tr key={account.id}>
            <td className="fw-semibold">{account.username}</td>
            <td>{account.displayName}</td>
            <td>{account.email ?? '—'}</td>
            <td>
              <Badge color={account.role === 'LIBRARIAN' ? 'success' : 'info'}>
                {account.role === 'LIBRARIAN' ? t('Librarian') : t('Member')}
              </Badge>
            </td>
            <td>{account.enabled ? t('Active') : <Badge color="secondary">{t('Disabled')}</Badge>}</td>
            <td className="text-end">
              {account.id !== user.id && (
                <Button size="sm" color={account.enabled ? 'danger' : 'secondary'} outline onClick={() => setEnabled(account, !account.enabled)} disabled={toggle.pending}>
                  {account.enabled ? t('Disable') : t('Enable')}
                </Button>
              )}
            </td>
          </tr>
        )}
      />
    </>
  );
}

function NewLibrarianForm({ onCreated }) {
  const [values, setValues, handleChange] = useFormFields(EMPTY_LIBRARIAN);
  const create = useAction();
  const t = useT();

  async function handleSubmit(event) {
    event.preventDefault();
    const clientErrors = {};
    if (!values.username.trim()) clientErrors.username = msg('Username is required.');
    if (!values.displayName.trim()) clientErrors.displayName = msg('Name is required.');
    if (values.password.length < 10) clientErrors.password = msg('Use at least 10 characters.');
    const body = { ...values, email: values.email.trim() || null };
    if (await create.run(() => api.post('/api/users/librarians', body), clientErrors)) {
      setValues(EMPTY_LIBRARIAN);
      onCreated();
    }
  }

  return (
    <Form onSubmit={handleSubmit} noValidate className="p-3 mb-3 border rounded bg-body-tertiary">
      <ErrorAlert error={Object.keys(create.fieldErrors).length ? null : create.error} onDismiss={create.clearError} />
      <Row>
        <Col md={3}><Field label={t('Username')} name="username" value={values.username} onChange={handleChange} error={t(create.fieldErrors.username)} /></Col>
        <Col md={3}><Field label={t('Name')} name="displayName" value={values.displayName} onChange={handleChange} error={t(create.fieldErrors.displayName)} /></Col>
        <Col md={3}><Field label={t('Email (optional)')} name="email" type="email" value={values.email} onChange={handleChange} error={t(create.fieldErrors.email)} /></Col>
        <Col md={3}><Field label={t('Initial password')} name="password" type="password" value={values.password} onChange={handleChange} error={t(create.fieldErrors.password)} autoComplete="new-password" /></Col>
      </Row>
      <Button color="primary" type="submit" disabled={create.pending}>{t('Create librarian')}</Button>
    </Form>
  );
}
