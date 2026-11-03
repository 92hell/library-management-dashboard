import { useState } from 'react';
import { Button, ButtonGroup, Col, Input, Label, Row } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert } from '../components/Feedback';
import PagedTable from '../components/PagedTable';
import PageHeader from '../components/PageHeader';
import SearchBox from '../components/SearchBox';
import { formatDate, formatMoney } from '../format';
import { useAction, useDebouncedValue, usePagedList } from '../hooks';

const STATUSES = [
  { value: 'UNPAID', label: 'Unpaid' },
  { value: 'PAID', label: 'Paid' },
  { value: 'WAIVED', label: 'Waived' },
  { value: '', label: 'All' },
];

/** The fines desk: late fees charged on returns; record a payment or waive one with a reason. */
export default function FineList() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('UNPAID');
  const q = useDebouncedValue(search.trim());
  const fines = usePagedList('/api/fines', { q, status });
  const action = useAction();

  /** Settling a fine removes it from the Unpaid view, like a deletion. */
  async function perform(call) {
    if (await action.run(call)) {
      if (status === 'UNPAID') fines.refreshAfterRemoval();
      else fines.reload();
    }
  }

  function pay(fine) {
    const amount = formatMoney(fine.amount, fine.currency);
    if (!window.confirm(`Record that ${fine.member.name} paid ${amount} for "${fine.book.title}"?`)) return;
    perform(() => api.post(`/api/fines/${fine.id}/pay`));
  }

  function waive(fine) {
    const reason = window.prompt(`Why waive the late fee for "${fine.book.title}" (${fine.member.name})?`, '');
    if (!reason || !reason.trim()) return;
    perform(() => api.post(`/api/fines/${fine.id}/waive`, { reason: reason.trim() }));
  }

  return (
    <>
      <PageHeader title="Fines" />
      <Row className="g-2 mb-3">
        <Col md={8}>
          <SearchBox
            id="fine-search"
            label="Search fines"
            placeholder="Search by member, library card ID or book"
            value={search}
            onChange={setSearch}
          />
        </Col>
        <Col md={4}>
          <Label for="fine-status" className="visually-hidden">
            Status
          </Label>
          <Input id="fine-status" type="select" value={status} onChange={(e) => setStatus(e.target.value)}>
            {STATUSES.map(({ value, label }) => (
              <option key={label} value={value}>
                {label}
              </option>
            ))}
          </Input>
        </Col>
      </Row>
      <ErrorAlert error={action.error} onDismiss={action.clearError} />
      <PagedTable
        list={fines}
        headers={['Book', 'Member', 'Returned', 'Late', 'Fee', 'Status', 'Actions']}
        emptyMessage={status === 'UNPAID' ? 'No unpaid fines.' : 'No fines found.'}
        renderRow={(fine) => (
          <tr key={fine.id}>
            <td className="fw-semibold">{fine.book.title}</td>
            <td>
              {fine.member.name}
              <span className="d-block small text-muted font-monospace">{fine.libraryCardId}</span>
            </td>
            <td className="text-nowrap">{formatDate(fine.returnDate)}</td>
            <td className="text-nowrap">
              {fine.daysLate} {fine.daysLate === 1 ? 'day' : 'days'} late
            </td>
            <td className="text-nowrap fw-semibold">{formatMoney(fine.amount, fine.currency)}</td>
            <td>
              <FineState fine={fine} />
            </td>
            <td className="text-nowrap">
              {fine.status === 'UNPAID' && (
                <ButtonGroup size="sm">
                  <Button color="success" onClick={() => pay(fine)} disabled={action.pending}>
                    Record payment
                  </Button>
                  <Button color="secondary" outline onClick={() => waive(fine)} disabled={action.pending}>
                    Waive
                  </Button>
                </ButtonGroup>
              )}
            </td>
          </tr>
        )}
      />
    </>
  );
}

function FineState({ fine }) {
  if (fine.status === 'UNPAID') return <span className="stamp stamp-overdue">Unpaid</span>;
  const label = fine.status === 'PAID' ? 'Paid' : 'Waived';
  return (
    <span>
      <span className="stamp stamp-returned">{label}</span>
      <span className="d-block small text-muted mt-1">
        {label} by {fine.settledBy}
        {fine.note && <> · {fine.note}</>}
      </span>
    </span>
  );
}
