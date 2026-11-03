import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { Badge, Form, ListGroup, ListGroupItem } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import Field from '../components/Field';
import FormActions from '../components/FormActions';
import { emptyToNull, formatDate } from '../format';
import { useAction, useApi, useFormFields } from '../hooks';

const EMPTY_AUTHOR = { name: '', dateOfBirth: '' };

export default function AuthorEdit() {
  const { id } = useParams();
  const isNew = id === 'new';
  const { isLibrarian } = useAuth();
  const author = useApi(isNew ? null : `/api/authors/${id}`);

  if (isNew && !isLibrarian) return <Navigate to="/authors" replace />;
  if (author.error) return <ErrorAlert error={author.error} />;
  if (author.loading) return <Loading />;

  return (
    <>
      <AuthorForm key={id} author={author.data} readOnly={!isLibrarian} />
      {!isNew && <AuthoredBooks authorId={id} />}
    </>
  );
}

function AuthorForm({ author, readOnly }) {
  const navigate = useNavigate();
  const [values, , handleChange] = useFormFields(
    author ? { name: author.name, dateOfBirth: author.dateOfBirth ?? '' } : EMPTY_AUTHOR,
  );
  const save = useAction();

  async function handleSubmit(event) {
    event.preventDefault();
    const clientErrors = values.name.trim() ? {} : { name: 'Name is required.' };
    const body = { name: values.name.trim(), dateOfBirth: emptyToNull(values.dateOfBirth) };
    const saved = await save.run(
      () => (author ? api.put(`/api/authors/${author.id}`, body) : api.post('/api/authors', body)),
      clientErrors,
    );
    if (saved) navigate('/authors');
  }

  const title = readOnly ? 'Author' : author ? 'Edit author' : 'Add author';

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
          error={save.fieldErrors.name}
          autoComplete="off"
        />
        <Field
          label="Date of birth"
          name="dateOfBirth"
          type="date"
          value={values.dateOfBirth}
          onChange={handleChange}
          error={save.fieldErrors.dateOfBirth}
        />
      </fieldset>
      <FormActions readOnly={readOnly} saving={save.pending} backTo="/authors" />
    </Form>
  );
}

function AuthoredBooks({ authorId }) {
  return (
    <section className="mt-4">
      <h2 className="h5">Books by this author</h2>
      <AuthoredBookList authorId={authorId} />
    </section>
  );
}

function AuthoredBookList({ authorId }) {
  const { data: books, error, loading } = useApi(`/api/authors/${authorId}/books`);

  if (error) return <ErrorAlert error={error} />;
  if (loading) return <Loading label="Loading books…" />;
  if (books.length === 0) return <p className="text-muted">No books found for this author.</p>;

  return (
    <ListGroup>
      {books.map((book) => (
        <ListGroupItem key={book.id} className="d-flex justify-content-between align-items-center gap-2">
          <span>
            <Link to={`/books/${book.id}`}>{book.title}</Link>
            <span className="text-muted small"> · published {formatDate(book.publishedDate)}</span>
          </span>
          {book.onLoan && (
            <Badge color="warning" className="text-dark">
              On loan
            </Badge>
          )}
        </ListGroupItem>
      ))}
    </ListGroup>
  );
}
