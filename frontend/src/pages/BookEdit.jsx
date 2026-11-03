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
import { msg, useT } from '../i18n';

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

/** Client-side checks; the messages are translated where they are shown. */
function validate(values) {
  const errors = {};
  if (!values.title.trim()) errors.title = msg('Title is required.');
  if (!values.publishedDate) errors.publishedDate = msg('Published date is required.');
  if (!values.authorId) errors.authorId = msg('Please select an author.');
  if (values.categories.length === 0) errors.categories = msg('Please select at least one category.');
  if (values.copies !== undefined && !(Number(values.copies) >= 0 && Number(values.copies) <= 50)) {
    errors.copies = msg('Between 0 and 50 copies.');
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
  const t = useT();

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

  const title = readOnly ? t('Book') : book ? t('Edit book') : t('Add book');
  const optionsError = authors.error ?? categories.error;
  // The link sits where the translation puts {link}, so each language keeps its own word order.
  const [beforeLink, afterLink] = t('{link} first, then choose them here.').split('{link}');

  return (
    <Form onSubmit={handleSubmit} noValidate className="p-4 border rounded shadow-sm">
      <h1 className="h3 mb-3">{title}</h1>
      <ErrorAlert error={save.error ?? optionsError} onDismiss={save.error ? save.clearError : undefined} />
      <fieldset disabled={readOnly}>
        <FormGroup>
          <Label for="isbn">{t('ISBN')}</Label>
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
              {t('Look up')}
            </Button>
          </InputGroup>
          {fieldErrors.isbn && <FormFeedback className="d-block">{t(fieldErrors.isbn)}</FormFeedback>}
          {!fieldErrors.isbn && (
            <FormText>{t('Optional. Look it up to fill in the details and show the cover from Open Library.')}</FormText>
          )}
        </FormGroup>
        <ErrorAlert error={lookup.error} onDismiss={lookup.clearError} />
        <Field label={t('Title')} name="title" value={values.title} onChange={handleChange} error={t(fieldErrors.title)} />
        <Field
          label={t('Publisher')}
          name="publisherName"
          value={values.publisherName}
          onChange={handleChange}
          error={t(fieldErrors.publisherName)}
        />
        {!book && (
          <Field
            label={t('Number of copies')}
            name="copies"
            type="number"
            min="0"
            max="50"
            value={values.copies}
            onChange={handleChange}
            error={t(fieldErrors.copies)}
            hint={t('Each copy gets its own barcode; you can add or withdraw copies later.')}
          />
        )}
        <Field
          label={t('Published date')}
          name="publishedDate"
          type="date"
          value={values.publishedDate}
          onChange={handleChange}
          error={t(fieldErrors.publishedDate)}
        />
        <Field
          label={t('Author')}
          name="authorId"
          type="select"
          value={values.authorId}
          onChange={handleChange}
          error={t(fieldErrors.authorId)}
          disabled={authors.loading}
        >
          <option value="">{authors.loading ? t('Loading authors…') : t('Select an author')}</option>
          {authors.data?.map((author) => (
            <option key={author.id} value={author.id}>
              {author.name}
            </option>
          ))}
        </Field>
        {missingAuthor && (
          <p className="small text-muted mt-n2">
            {t('Open Library lists the author as “{name}”, who is not in the catalogue yet.', { name: missingAuthor })}{' '}
            {beforeLink}
            <Link to="/authors/new">{t('Add the author')}</Link>
            {afterLink}
          </p>
        )}
        <FormGroup tag="fieldset">
          <legend className="form-label fs-6">{t('Categories')}</legend>
          {categories.loading && <Loading label={t('Loading categories…')} />}
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
          {fieldErrors.categories && <FormFeedback className="d-block">{t(fieldErrors.categories)}</FormFeedback>}
        </FormGroup>
        <Field
          label={t('Description')}
          name="description"
          type="textarea"
          rows="4"
          maxLength="2000"
          value={values.description}
          onChange={handleChange}
          error={t(fieldErrors.description)}
          hint={t('Shown on the book\'s page in the catalogue.')}
        />
      </fieldset>
      <FormActions readOnly={readOnly} saving={save.pending} backTo="/books" />
    </Form>
  );
}
