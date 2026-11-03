import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { Form, FormFeedback, FormGroup, Input, Label } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import Field from '../components/Field';
import FormActions from '../components/FormActions';
import { emptyToNull } from '../format';
import { useAction, useApi, useFormFields } from '../hooks';

const EMPTY_BOOK = { title: '', publishedDate: '', publisherName: '', authorId: '', categories: [] };

function toFormValues(book) {
  return {
    title: book.title,
    publishedDate: book.publishedDate ?? '',
    publisherName: book.publisherName ?? '',
    authorId: String(book.author?.id ?? ''),
    categories: book.categories,
  };
}

function validate(values) {
  const errors = {};
  if (!values.title.trim()) errors.title = 'Title is required.';
  if (!values.publishedDate) errors.publishedDate = 'Published date is required.';
  if (!values.authorId) errors.authorId = 'Please select an author.';
  if (values.categories.length === 0) errors.categories = 'Please select at least one category.';
  return errors;
}

export default function BookEdit() {
  const { id } = useParams();
  const isNew = id === 'new';
  const { isLibrarian } = useAuth();
  const book = useApi(isNew ? null : `/api/books/${id}`);

  if (isNew && !isLibrarian) return <Navigate to="/books" replace />;
  if (book.error) return <ErrorAlert error={book.error} />;
  if (book.loading) return <Loading />;

  return <BookForm key={id} book={book.data} readOnly={!isLibrarian} />;
}

function BookForm({ book, readOnly }) {
  const navigate = useNavigate();
  const [values, setValues, handleChange] = useFormFields(book ? toFormValues(book) : EMPTY_BOOK);
  const authors = useApi('/api/authors/options');
  const categories = useApi('/api/books/categories');
  const save = useAction();
  const { fieldErrors } = save;

  function toggleCategory(category) {
    setValues((current) => ({
      ...current,
      categories: current.categories.includes(category)
        ? current.categories.filter((c) => c !== category)
        : [...current.categories, category],
    }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const body = {
      title: values.title.trim(),
      publishedDate: emptyToNull(values.publishedDate),
      publisherName: emptyToNull(values.publisherName.trim()),
      authorId: Number(values.authorId),
      categories: values.categories,
    };
    const saved = await save.run(
      () => (book ? api.put(`/api/books/${book.id}`, body) : api.post('/api/books', body)),
      validate(values),
    );
    if (saved) navigate('/books');
  }

  const title = readOnly ? 'Book' : book ? 'Edit book' : 'Add book';
  const optionsError = authors.error ?? categories.error;

  return (
    <Form onSubmit={handleSubmit} noValidate className="p-4 border rounded shadow-sm">
      <h1 className="h3 mb-3">{title}</h1>
      <ErrorAlert error={save.error ?? optionsError} onDismiss={save.error ? save.clearError : undefined} />
      <fieldset disabled={readOnly}>
        <Field label="Title" name="title" value={values.title} onChange={handleChange} error={fieldErrors.title} />
        <Field
          label="Publisher"
          name="publisherName"
          value={values.publisherName}
          onChange={handleChange}
          error={fieldErrors.publisherName}
        />
        <Field
          label="Published date"
          name="publishedDate"
          type="date"
          value={values.publishedDate}
          onChange={handleChange}
          error={fieldErrors.publishedDate}
        />
        <Field
          label="Author"
          name="authorId"
          type="select"
          value={values.authorId}
          onChange={handleChange}
          error={fieldErrors.authorId}
          disabled={authors.loading}
        >
          <option value="">{authors.loading ? 'Loading authors…' : 'Select an author'}</option>
          {authors.data?.map((author) => (
            <option key={author.id} value={author.id}>
              {author.name}
            </option>
          ))}
        </Field>
        <FormGroup tag="fieldset">
          <legend className="form-label fs-6">Categories</legend>
          {categories.loading && <Loading label="Loading categories…" />}
          {categories.data?.map((category) => (
            <FormGroup check inline key={category}>
              <Input
                id={`category-${category}`}
                type="checkbox"
                checked={values.categories.includes(category)}
                onChange={() => toggleCategory(category)}
                invalid={Boolean(fieldErrors.categories)}
              />
              <Label check for={`category-${category}`}>
                {category}
              </Label>
            </FormGroup>
          ))}
          {fieldErrors.categories && <FormFeedback className="d-block">{fieldErrors.categories}</FormFeedback>}
        </FormGroup>
      </fieldset>
      <FormActions readOnly={readOnly} saving={save.pending} backTo="/books" />
    </Form>
  );
}
