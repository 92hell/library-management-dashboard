import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { Form } from 'reactstrap';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import Field from '../components/Field';
import FormActions from '../components/FormActions';
import { emptyToNull, todayIso } from '../format';
import { useAction, useApi, useFormFields } from '../hooks';

function toFormValues(loan) {
  return {
    bookId: String(loan.book.id),
    copyBarcode: '',
    memberId: String(loan.member.id),
    borrowDate: loan.borrowDate ?? '',
    dueDate: loan.dueDate ?? '',
    returnDate: loan.returnDate ?? '',
  };
}

function validate(values) {
  const errors = {};
  if (!values.bookId && !values.copyBarcode.trim()) errors.bookId = 'Scan a barcode or select a book.';
  if (!values.memberId) errors.memberId = 'Please select a member.';
  if (!values.borrowDate) errors.borrowDate = 'Borrow date is required.';
  if (values.returnDate && values.borrowDate && values.returnDate < values.borrowDate) {
    errors.returnDate = 'Return date cannot be before the borrow date.';
  }
  if (values.dueDate && values.borrowDate && values.dueDate < values.borrowDate) {
    errors.dueDate = 'Due date cannot be before the borrow date.';
  }
  return errors;
}

/** Makes sure the option already on the record is listed, even if the options endpoint omits it. */
function withCurrent(options = [], current) {
  if (!current || options.some((option) => option.id === current.id)) return options;
  return [current, ...options];
}

export default function BorrowedBookEdit() {
  const { id } = useParams();
  const isNew = id === 'new';
  const { isLibrarian } = useAuth();
  const loan = useApi(isNew ? null : `/api/borrowed-books/${id}`);

  if (isNew && !isLibrarian) return <Navigate to="/borrowed-books" replace />;
  if (loan.error) return <ErrorAlert error={loan.error} />;
  if (loan.loading) return <Loading />;

  // Requests are decided from the list (approve/reject); only lent or returned loans are edited here.
  const editable = isLibrarian && (!loan.data || ['ACTIVE', 'RETURNED'].includes(loan.data.status));
  return <LoanForm key={id} loan={loan.data} readOnly={!editable} />;
}

function LoanForm({ loan, readOnly }) {
  const navigate = useNavigate();
  const [values, , handleChange] = useFormFields(
    loan ? toFormValues(loan) : { bookId: '', copyBarcode: '', memberId: '', borrowDate: todayIso(), dueDate: '', returnDate: '' },
  );
  const books = useApi('/api/books/options');
  const members = useApi('/api/members/options');
  const save = useAction();
  const { fieldErrors } = save;

  const bookOptions = withCurrent(books.data, loan?.book);
  const memberOptions = withCurrent(members.data, loan?.member);

  async function handleSubmit(event) {
    event.preventDefault();
    const body = {
      bookId: values.bookId ? Number(values.bookId) : null,
      copyBarcode: values.copyBarcode.trim() || null,
      memberId: Number(values.memberId),
      borrowDate: values.borrowDate,
      dueDate: emptyToNull(values.dueDate),
      returnDate: emptyToNull(values.returnDate),
    };
    const saved = await save.run(
      () => (loan ? api.put(`/api/borrowed-books/${loan.id}`, body) : api.post('/api/borrowed-books', body)),
      validate(values),
    );
    if (saved) navigate('/borrowed-books');
  }

  const title = readOnly ? 'Loan' : loan ? 'Edit loan' : 'Lend a book at the desk';
  const optionsError = books.error ?? members.error;

  return (
    <Form onSubmit={handleSubmit} noValidate className="p-4 border rounded shadow-sm">
      <h1 className="h3 mb-3">{title}</h1>
      <ErrorAlert error={save.error ?? optionsError} onDismiss={save.error ? save.clearError : undefined} />
      <fieldset disabled={readOnly}>
        <Field
          label="Scan a barcode (or choose the book below)"
          name="copyBarcode"
          placeholder={loan?.copy ? `Currently ${loan.copy.barcode}` : 'e.g. BC-7KQ2XM9P'}
          value={values.copyBarcode}
          onChange={handleChange}
          error={fieldErrors.copyBarcode}
          autoComplete="off"
          hint="With a barcode, that exact copy is lent; with only a book, any copy on the shelf."
        />
        <Field
          label="Book"
          name="bookId"
          type="select"
          value={values.bookId}
          onChange={handleChange}
          error={fieldErrors.bookId}
          disabled={books.loading}
        >
          <option value="">{books.loading ? 'Loading books…' : 'Select a book'}</option>
          {bookOptions.map((book) => {
            // The book on this record is "on loan" because of this very loan, so it stays selectable.
            const unavailable = book.onLoan && book.id !== loan?.book.id;
            const label = book.authorName ? `${book.title} by ${book.authorName}` : book.title;
            const copies = book.totalCopies != null ? ` (${book.availableCopies} of ${book.totalCopies} on shelf)` : '';
            return (
              <option key={book.id} value={book.id} disabled={unavailable}>
                {unavailable ? `${label} (all copies on loan)` : `${label}${copies}`}
              </option>
            );
          })}
        </Field>
        <Field
          label="Member"
          name="memberId"
          type="select"
          value={values.memberId}
          onChange={handleChange}
          error={fieldErrors.memberId}
          disabled={members.loading}
        >
          <option value="">{members.loading ? 'Loading members…' : 'Select a member'}</option>
          {memberOptions.map((member) => (
            <option key={member.id} value={member.id}>
              {member.name}
            </option>
          ))}
        </Field>
        <Field
          label="Borrow date"
          name="borrowDate"
          type="date"
          value={values.borrowDate}
          onChange={handleChange}
          error={fieldErrors.borrowDate}
        />
        <Field
          label="Due date (optional, defaults to the loan period)"
          name="dueDate"
          type="date"
          value={values.dueDate}
          onChange={handleChange}
          error={fieldErrors.dueDate}
        />
        <Field
          label="Return date (optional)"
          name="returnDate"
          type="date"
          value={values.returnDate}
          onChange={handleChange}
          error={fieldErrors.returnDate}
        />
      </fieldset>
      <FormActions readOnly={readOnly} saving={save.pending} backTo="/borrowed-books" />
    </Form>
  );
}
