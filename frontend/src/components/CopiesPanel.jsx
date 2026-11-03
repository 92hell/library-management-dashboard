import { Button, Col, Form, Row, Table } from 'reactstrap';
import { api } from '../api';
import { formatDate } from '../format';
import { useAction, useApi, useFormFields } from '../hooks';
import { ErrorAlert, Loading } from './Feedback';
import Field from './Field';

/** The physical copies of a book: barcode, shelf, who has it; add and withdraw copies. */
export default function CopiesPanel({ bookId }) {
  const copies = useApi(`/api/books/${bookId}/copies`);
  const [values, setValues, handleChange] = useFormFields({ barcode: '', shelfLocation: '' });
  const action = useAction();

  async function addCopy(event) {
    event.preventDefault();
    const body = { barcode: values.barcode.trim() || null, shelfLocation: values.shelfLocation.trim() || null };
    if (await action.run(() => api.post(`/api/books/${bookId}/copies`, body))) {
      setValues({ barcode: '', shelfLocation: values.shelfLocation });
      copies.reload();
    }
  }

  async function withdraw(copy) {
    if (!window.confirm(`Withdraw copy ${copy.barcode} from the collection (lost, damaged or discarded)?`)) return;
    if (await action.run(() => api.delete(`/api/copies/${copy.id}`))) copies.reload();
  }

  return (
    <section className="p-4 border rounded shadow-sm mt-4" aria-labelledby="copies-heading">
      <h2 id="copies-heading" className="h5 mb-3">Copies</h2>
      <ErrorAlert error={Object.keys(action.fieldErrors).length ? null : action.error} onDismiss={action.clearError} />
      {copies.error && <ErrorAlert error={copies.error} />}
      {!copies.data ? (
        <Loading />
      ) : copies.data.length === 0 ? (
        <p className="empty-state">No copies yet. Add the first one below.</p>
      ) : (
        <Table responsive className="align-middle">
          <thead className="table-library">
            <tr>
              <th scope="col">Barcode</th>
              <th scope="col">Shelf</th>
              <th scope="col">Status</th>
              <th scope="col" aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {copies.data.map((copy) => (
              <tr key={copy.id}>
                <td className="font-monospace">{copy.barcode}</td>
                <td>{copy.shelfLocation ?? '—'}</td>
                <td>
                  {copy.loan ? (
                    <span>
                      <span className={`stamp ${copy.loan.overdue ? 'stamp-overdue' : 'stamp-active'}`}>
                        {copy.loan.overdue ? 'Overdue' : 'On loan'}
                      </span>{' '}
                      <span className="small">
                        {copy.loan.memberName}, due {formatDate(copy.loan.dueDate)}
                      </span>
                    </span>
                  ) : (
                    <span className="stamp stamp-returned">On shelf</span>
                  )}
                </td>
                <td className="text-end">
                  {!copy.loan && (
                    <Button size="sm" color="danger" outline onClick={() => withdraw(copy)} disabled={action.pending}>
                      Withdraw
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      <Form onSubmit={addCopy} noValidate className="mt-3">
        <Row className="g-2 align-items-end">
          <Col md={5}>
            <Field
              label="New copy barcode"
              name="barcode"
              placeholder="Leave empty to generate one"
              value={values.barcode}
              onChange={handleChange}
              error={action.fieldErrors.barcode}
            />
          </Col>
          <Col md={4}>
            <Field
              label="Shelf location"
              name="shelfLocation"
              placeholder="e.g. FIC HIR"
              value={values.shelfLocation}
              onChange={handleChange}
              error={action.fieldErrors.shelfLocation}
            />
          </Col>
          <Col md={3} className="mb-3">
            <Button color="success" type="submit" className="w-100" disabled={action.pending}>
              Add copy
            </Button>
          </Col>
        </Row>
      </Form>
    </section>
  );
}
