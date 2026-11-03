import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { Form, FormGroup, Input, Label } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import Field from '../components/Field';
import FormActions from '../components/FormActions';
import MemberLoans from '../components/MemberLoans';
import { emptyToNull } from '../format';
import { useAction, useApi, useFormFields } from '../hooks';

const EMPTY_MEMBER = { name: '', dateOfBirth: '', email: '', phone: '', active: true };

function toFormValues(member) {
  return {
    name: member.name,
    dateOfBirth: member.dateOfBirth ?? '',
    email: member.email ?? '',
    phone: member.phone ?? '',
    active: member.active,
  };
}

function validate(values) {
  const errors = {};
  if (!values.name.trim()) errors.name = 'Name is required.';
  if (!values.email.trim()) errors.email = 'Email is required.';
  if (!values.phone.trim()) errors.phone = 'Phone is required.';
  if (!values.dateOfBirth) errors.dateOfBirth = 'Date of birth is required.';
  return errors;
}

export default function MemberEdit() {
  const { id } = useParams();
  const isNew = id === 'new';
  const { isLibrarian } = useAuth();
  const member = useApi(isNew ? null : `/api/members/${id}`);

  if (isNew && !isLibrarian) return <Navigate to="/members" replace />;
  if (member.error) return <ErrorAlert error={member.error} />;
  if (member.loading) return <Loading />;

  return (
    <>
      <MemberForm key={id} member={member.data} readOnly={!isLibrarian} />
      {!isNew && (
        <section className="mt-4">
          <h2 className="h5">Borrowing history</h2>
          <MemberLoans memberId={id} />
        </section>
      )}
    </>
  );
}

function MemberForm({ member, readOnly }) {
  const navigate = useNavigate();
  const [values, , handleChange] = useFormFields(member ? toFormValues(member) : EMPTY_MEMBER);
  const save = useAction();
  const { fieldErrors } = save;

  async function handleSubmit(event) {
    event.preventDefault();
    const body = {
      name: values.name.trim(),
      dateOfBirth: emptyToNull(values.dateOfBirth),
      email: values.email.trim(),
      phone: values.phone.trim(),
      active: values.active,
    };
    const saved = await save.run(
      () => (member ? api.put(`/api/members/${member.id}`, body) : api.post('/api/members', body)),
      validate(values),
    );
    if (saved) navigate('/members');
  }

  const title = readOnly ? 'Member' : member ? 'Edit member' : 'Add member';

  return (
    <Form onSubmit={handleSubmit} noValidate className="p-4 border rounded shadow-sm">
      <h1 className="h3 mb-3">{title}</h1>
      <ErrorAlert error={save.error} onDismiss={save.clearError} />
      <fieldset disabled={readOnly}>
        <Field
          label="Name"
          name="name"
          value={values.name}
          onChange={handleChange}
          error={fieldErrors.name}
          autoComplete="off"
        />
        <Field
          label="Email"
          name="email"
          type="email"
          value={values.email}
          onChange={handleChange}
          error={fieldErrors.email}
          autoComplete="off"
        />
        <Field
          label="Phone"
          name="phone"
          type="tel"
          value={values.phone}
          onChange={handleChange}
          error={fieldErrors.phone}
          autoComplete="off"
        />
        <Field
          label="Date of birth"
          name="dateOfBirth"
          type="date"
          value={values.dateOfBirth}
          onChange={handleChange}
          error={fieldErrors.dateOfBirth}
        />
        <FormGroup switch>
          <Input id="active" name="active" type="switch" checked={values.active} onChange={handleChange} />
          <Label check for="active">
            Active member
          </Label>
        </FormGroup>
      </fieldset>
      <FormActions readOnly={readOnly} saving={save.pending} backTo="/members" />
    </Form>
  );
}
