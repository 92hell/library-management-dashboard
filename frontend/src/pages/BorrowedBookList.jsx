import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button, ButtonGroup, Col, Input, Label, Row } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert } from '../components/Feedback';
import BookCover from '../components/BookCover';
import LoanStatus from '../components/LoanStatus';
import PagedTable from '../components/PagedTable';
import PageHeader from '../components/PageHeader';
import SearchBox from '../components/SearchBox';
import { formatDate } from '../format';
import { useAction, useDebouncedValue, usePagedList } from '../hooks';

const STATUSES = [
  { value: 'all', label: 'All' },
  { value: 'pending', label: 'Pending requests' },
  { value: 'waitlisted', label: 'In queue' },
  { value: 'ready', label: 'Ready for pickup' },
  { value: 'active', label: 'On loan' },
  { value: 'overdue', label: 'Overdue' },
  { value: 'returned', label: 'Returned' },
  { value: 'rejected', label: 'Declined' },
  { value: 'cancelled', label: 'Cancelled' },
  { value: 'expired', label: 'Not collected' },
];
const EDITABLE = ['ACTIVE', 'RETURNED'];

/** All loans and requests, with the actions a librarian takes on them. ?status= selects the filter. */
export default function BorrowedBookList() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = STATUSES.some(({ value }) => value === searchParams.get('status')) ? searchParams.get('status') : 'all';
  const [search, setSearch] = useState('');
  const [borrowDate, setBorrowDate] = useState('');
  const q = useDebouncedValue(search.trim());
  const loans = usePagedList('/api/borrowed-books', { q, borrowDate, status });
  const action = useAction();
  const [returnBarcode, setReturnBarcode] = useState('');
  const [returned, setReturned] = useState(null);

  /** Desk return: scan the copy and its loan is closed. */
  async function returnScanned(event) {
    event.preventDefault();
    const barcode = returnBarcode.trim();
    if (!barcode) return;
    setReturned(null);
    let result = null;
    if (await action.run(async () => {
      result = await api.post('/api/borrowed-books/return-by-barcode', { barcode });
    })) {
      setReturned(result);
      setReturnBarcode('');
      loans.reload();
    }
  }

  function setStatus(value) {
    setSearchParams(value === 'all' ? {} : { status: value }, { replace: true });
  }

  /** Runs a workflow action; under a narrowed filter the row leaves the list, like a deletion. */
  async function perform(call) {
    if (await action.run(call)) {
      if (status === 'all') loans.reload();
      else loans.refreshAfterRemoval();
    }
  }

  function approve(loan) {
    perform(() => api.post(`/api/borrowed-books/${loan.id}/approve`, {}));
  }

  function reject(loan) {
    const reason = window.prompt(`Decline the request for "${loan.book.title}"? Optional reason for ${loan.member.name}:`, '');
    if (reason === null) return;
    perform(() => api.post(`/api/borrowed-books/${loan.id}/reject`, { reason: reason.trim() || null }));
  }

  function handOver(loan) {
    perform(() => api.post(`/api/borrowed-books/${loan.id}/checkout`));
  }

  function renew(loan) {
    perform(() => api.post(`/api/borrowed-books/${loan.id}/renew`));
  }

  function markReturned(loan) {
    perform(() => api.post(`/api/borrowed-books/${loan.id}/return`));
  }

  function remove(loan) {
    if (!window.confirm(`Delete the record of "${loan.book.title}" for ${loan.member.name}?`)) return;
    perform(() => api.delete(`/api/borrowed-books/${loan.id}`));
  }

  return (
    <>
      <PageHeader title="Loans" addLabel="Lend a book" addTo="/borrowed-books/new" />
      <form className="d-flex gap-2 mb-3" onSubmit={returnScanned}>
        <Label for="return-barcode" className="visually-hidden">
          Return by barcode
        </Label>
        <Input
          id="return-barcode"
          placeholder="Return by barcode: scan or type, then Enter"
          value={returnBarcode}
          onChange={(e) => setReturnBarcode(e.target.value)}
          autoComplete="off"
        />
        <Button color="success" type="submit" disabled={action.pending || !returnBarcode.trim()}>
          Return
        </Button>
      </form>
      {returned && (
        <div className="alert alert-success d-flex justify-content-between align-items-start" role="status">
          <span>
            Returned &quot;{returned.book.title}&quot; from {returned.member.name}
            {returned.copy ? ` (${returned.copy.barcode})` : ''}.
          </span>
          <button type="button" className="btn-close" aria-label="Dismiss" onClick={() => setReturned(null)} />
        </div>
      )}
      <Row className="g-2 mb-3">
        <Col md={6}>
          <SearchBox
            id="loan-search"
            label="Search loans"
            placeholder="Search by member, library card ID, book or author"
            value={search}
            onChange={setSearch}
          />
        </Col>
        <Col xs={6} md={3}>
          <Label for="loan-borrow-date" className="visually-hidden">
            Borrow date
          </Label>
          <Input id="loan-borrow-date" type="date" value={borrowDate} onChange={(e) => setBorrowDate(e.target.value)} title="Borrow date" />
        </Col>
        <Col xs={6} md={3}>
          <Label for="loan-status" className="visually-hidden">
            Status
          </Label>
          <Input id="loan-status" type="select" value={status} onChange={(e) => setStatus(e.target.value)}>
            {STATUSES.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Input>
        </Col>
      </Row>
      <ErrorAlert error={action.error} onDismiss={action.clearError} />
      <PagedTable
        list={loans}
        headers={['Book', 'Member', 'Borrowed', 'Due', 'Status', 'Actions']}
        emptyMessage="No loans found."
        renderRow={(loan) => (
          <tr key={loan.id}>
            <td>
              <div className="d-flex align-items-center gap-3">
                <BookCover title={loan.book.title} />
                <div>
                  <span className="fw-semibold">{loan.book.title}</span>
                  <span className="d-block small text-muted">
                    {loan.book.authorName}
                    {loan.copy && <span className="font-monospace ms-2">{loan.copy.barcode}</span>}
                  </span>
                </div>
              </div>
            </td>
            <td>{loan.member.name}</td>
            <td className="text-nowrap">{loan.status === 'PENDING' ? 'Requested' : formatDate(loan.borrowDate)}</td>
            <td className="text-nowrap">{formatDate(loan.dueDate)}</td>
            <td>
              <LoanStatus loan={loan} />
            </td>
            <td className="text-nowrap">
              <ButtonGroup size="sm">
                {loan.status === 'PENDING' && (
                  <Button color="success" onClick={() => approve(loan)} disabled={action.pending}>
                    Approve
                  </Button>
                )}
                {loan.status === 'READY' && (
                  <Button color="success" onClick={() => handOver(loan)} disabled={action.pending}>
                    Hand over
                  </Button>
                )}
                {['PENDING', 'WAITLISTED', 'READY'].includes(loan.status) && (
                  <Button color="secondary" onClick={() => reject(loan)} disabled={action.pending}>
                    Reject
                  </Button>
                )}
                {loan.status === 'ACTIVE' && (
                  <Button color="success" onClick={() => markReturned(loan)} disabled={action.pending}>
                    Mark returned
                  </Button>
                )}
                {loan.canRenew && (
                  <Button color="primary" outline onClick={() => renew(loan)} disabled={action.pending}>
                    Renew
                  </Button>
                )}
                {EDITABLE.includes(loan.status) && (
                  <Button color="primary" tag={Link} to={`/borrowed-books/${loan.id}`}>
                    Edit
                  </Button>
                )}
                <Button color="danger" outline onClick={() => remove(loan)} disabled={action.pending}>
                  Delete
                </Button>
              </ButtonGroup>
            </td>
          </tr>
        )}
      />
    </>
  );
}
