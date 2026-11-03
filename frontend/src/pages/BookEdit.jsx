import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { Button, Form, FormFeedback, FormGroup, FormText, Input, InputGroup, Label } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import Field from '../components/Field';
import CopiesPanel from '../components/CopiesPanel';
import FormActions from '../components/FormActions';
import { emptyToNull } from '../format';
import { useAction, useApi, useFormFields } from '../hooks';

const EMPTY_BOOK = {
  title: '',
  publishedDate: '',
  publisherName: '',
  authorId: '',
  categories: [],
  copies: '1',
  isbn: '',
  description: '',
};

function toFormValues(book) {
  return {
    title: book.title,
    publishedDate: book.publishedDate ?? '',
    publisherName: book.publisherName ?? '',
    authorId: String(book.author?.id ?? ''),
    categories: book.categories,
    isbn: book.isbn ?? '',
    description: book.description ?? '',
  };
}

function validate(values) {
  const errors = {};
  if (!values.title.trim()) errors.title = 'Title is required.';
  if (!values.publishedDate) errors.publishedDate = 'Published date is required.';
  if (!values.authorId) errors.authorId = 'Please select an author.';
  if (values.categories.length === 0) errors.categories = 'Please select at least one category.';
  if (values.copies !== undefined && !(Number(values.copies) >= 0 && Number(values.copies) <= 50)) {
    errors.copies = 'Between 0 and 50 copies.';
  }
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

  return (
    <>
      <BookForm key={id} book={book.data} readOnly={!isLibrarian} />
      {!isNew && isLibrarian && <CopiesPanel bookId={book.data.id} />}
    </>
  );
}

function BookForm({ book, readOnly }) {
  const navigate = useNavigate();
  const [values, setValues, handleChange] = useFormFields(book ? toFormValues(book) : EMPTY_BOOK);
  const authors = useApi('/api/authors/options');
  const categories = useApi('/api/books/categories');
  const save = useAction();
  const lookup = useAction();
  const [missingAuthor, setMissingAuthor] = useState(null);
  const { fieldErrors } = save;

  /** Fills the form from Open Library; what it does not know keeps the value already typed. */
  async function lookUp() {
    let found = null;
    setMissingAuthor(null);
    if (!(await lookup.run(async () => {
      found = await api.get('/api/books/lookup', { isbn: values.isbn.trim() });
    }))) return;
    setValues((current) => ({
      ...current,
      isbn: found.isbn,
      title: found.title ?? current.title,
      publisherName: found.publisherName ?? current.publisherName,
      publishedDate: found.publishedDate ?? current.publishedDate,
      authorId: found.authorId ? String(found.authorId) : current.authorId,
      categories: [...new Set([...current.categories, ...found.categories])],
    }));
    if (!found.authorId && found.authorName) setMissingAuthor(found.authorName);
  }

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
      isbn: emptyToNull(values.isbn.trim()),
      description: emptyToNull(values.description.trim()),
      ...(book ? {} : { copies: Number(values.copies) }),
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
        <FormGroup>
          <Label for="isbn">ISBN</Label>
          <InputGroup>
            <Input
              id="isbn"
              name="isbn"
              value={values.isbn}
              onChange={handleChange}
              invalid={Boolean(fieldErrors.isbn)}
              autoComplete="off"
              inputMode="numeric"
            />
            <Button type="button" color="secondary" outline onClick={lookUp} disabled={lookup.pending || !values.isbn.trim()}>
              Look up
            </Button>
          </InputGroup>
          {fieldErrors.isbn && <FormFeedback className="d-block">{fieldErrors.isbn}</FormFeedback>}
          {!fieldErrors.isbn && <FormText>Optional. Look it up to fill in the details and show the cover from Open Library.</FormText>}
        </FormGroup>
        <ErrorAlert error={lookup.error} onDismiss={lookup.clearError} />
        <Field label="Title" name="title" value={values.title} onChange={handleChange} error={fieldErrors.title} />
        <Field
          label="Publisher"
          name="publisherName"
          value={values.publisherName}
          onChange={handleChange}
          error={fieldErrors.publisherName}
        />
        {!book && (
          <Field
            label="Number of copies"
            name="copies"
            type="number"
            min="0"
            max="50"
            value={values.copies}
            onChange={handleChange}
            error={fieldErrors.copies}
            hint="Each copy gets its own barcode; you can add or withdraw copies later."
          />
        )}
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
        {missingAuthor && (
          <p className="small text-muted mt-n2">
            Open Library lists the author as “{missingAuthor}”, who is not in the catalogue yet.{' '}
            <Link to="/authors/new">Add the author</Link> first, then choose them here.
          </p>
        )}
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
        <Field
          label="Description"
          name="description"
          type="textarea"
          rows="4"
          maxLength="2000"
          value={values.description}
          onChange={handleChange}
          error={fieldErrors.description}
          hint="Shown on the book's page in the catalogue."
        />
      </fieldset>
      <FormActions readOnly={readOnly} saving={save.pending} backTo="/books" />
    </Form>
  );
}
