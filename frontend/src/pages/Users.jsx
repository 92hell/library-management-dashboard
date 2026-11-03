import { useState } from 'react';
import { Badge, Button, Col, Collapse, Form, Input, Label, Row } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert } from '../components/Feedback';
import Field from '../components/Field';
import PagedTable from '../components/PagedTable';
import SearchBox from '../components/SearchBox';
import { useAction, useDebouncedValue, useFormFields, usePagedList } from '../hooks';

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

  async function setEnabled(account, enabled) {
    const verb = enabled ? 'Enable' : 'Disable';
    if (!window.confirm(`${verb} the account "${account.username}"?`)) return;
    if (await toggle.run(() => api.put(`/api/users/${account.id}/enabled`, { enabled }))) users.reload();
  }

  return (
    <>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 mb-0">Users</h1>
        <Button color="success" onClick={() => setAdding((open) => !open)} aria-expanded={adding}>
          Add librarian
        </Button>
      </div>
      <Collapse isOpen={adding}>
        <NewLibrarianForm onCreated={() => { setAdding(false); users.reload(); }} />
      </Collapse>
      <Row className="g-2 mb-3">
        <Col md={8}>
          <SearchBox id="user-search" label="Search users" placeholder="Search by username, name or email" value={search} onChange={setSearch} />
        </Col>
        <Col md={4}>
          <Label for="user-role" className="visually-hidden">Role</Label>
          <Input id="user-role" type="select" value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="">All roles</option>
            <option value="LIBRARIAN">Librarians</option>
            <option value="CUSTOMER">Members</option>
          </Input>
        </Col>
      </Row>
      <ErrorAlert error={toggle.error} onDismiss={toggle.clearError} />
      <PagedTable
        list={users}
        headers={['Username', 'Name', 'Email', 'Role', 'Status', '']}
        emptyMessage="No accounts found."
        renderRow={(account) => (
          <tr key={account.id}>
            <td className="fw-semibold">{account.username}</td>
            <td>{account.displayName}</td>
            <td>{account.email ?? '—'}</td>
            <td>
              <Badge color={account.role === 'LIBRARIAN' ? 'success' : 'info'}>
                {account.role === 'LIBRARIAN' ? 'Librarian' : 'Member'}
              </Badge>
            </td>
            <td>{account.enabled ? 'Active' : <Badge color="secondary">Disabled</Badge>}</td>
            <td className="text-end">
              {account.id !== user.id && (
                <Button size="sm" color={account.enabled ? 'danger' : 'secondary'} outline onClick={() => setEnabled(account, !account.enabled)} disabled={toggle.pending}>
                  {account.enabled ? 'Disable' : 'Enable'}
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

  async function handleSubmit(event) {
    event.preventDefault();
    const clientErrors = {};
    if (!values.username.trim()) clientErrors.username = 'Username is required.';
    if (!values.displayName.trim()) clientErrors.displayName = 'Name is required.';
    if (values.password.length < 10) clientErrors.password = 'Use at least 10 characters.';
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
        <Col md={3}><Field label="Username" name="username" value={values.username} onChange={handleChange} error={create.fieldErrors.username} /></Col>
        <Col md={3}><Field label="Name" name="displayName" value={values.displayName} onChange={handleChange} error={create.fieldErrors.displayName} /></Col>
        <Col md={3}><Field label="Email (optional)" name="email" type="email" value={values.email} onChange={handleChange} error={create.fieldErrors.email} /></Col>
        <Col md={3}><Field label="Initial password" name="password" type="password" value={values.password} onChange={handleChange} error={create.fieldErrors.password} autoComplete="new-password" /></Col>
      </Row>
      <Button color="primary" type="submit" disabled={create.pending}>Create librarian</Button>
    </Form>
  );
}
