import { useState } from 'react';
import { Button, ButtonGroup, Col, Input, Label, Row } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert } from '../components/Feedback';
import PagedTable from '../components/PagedTable';
import PageHeader from '../components/PageHeader';
import SearchBox from '../components/SearchBox';
import { formatDate, formatMoney } from '../format';
import { useAction, useDebouncedValue, usePagedList } from '../hooks';
import { msg, useT } from '../i18n';

const STATUSES = [
  { value: 'UNPAID', label: msg('Unpaid') },
  { value: 'PAID', label: msg('Paid') },
  { value: 'WAIVED', label: msg('Waived') },
  { value: '', label: msg('All') },
];

/** The fines desk: late fees charged on returns; record a payment or waive one with a reason. */
export default function FineList() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('UNPAID');
  const q = useDebouncedValue(search.trim());
  const fines = usePagedList('/api/fines', { q, status });
  const action = useAction();
  const t = useT();

  /** Settling a fine removes it from the Unpaid view, like a deletion. */
  async function perform(call) {
    if (await action.run(call)) {
      if (status === 'UNPAID') fines.refreshAfterRemoval();
      else fines.reload();
    }
  }

  function pay(fine) {
    const amount = formatMoney(fine.amount, fine.currency);
    if (!window.confirm(t('Record that {name} paid {amount} for "{title}"?', { name: fine.member.name, amount, title: fine.book.title }))) {
      return;
    }
    perform(() => api.post(`/api/fines/${fine.id}/pay`));
  }

  function waive(fine) {
    const reason = window.prompt(t('Why waive the late fee for "{title}" ({name})?', { title: fine.book.title, name: fine.member.name }), '');
    if (!reason || !reason.trim()) return;
    perform(() => api.post(`/api/fines/${fine.id}/waive`, { reason: reason.trim() }));
  }

  return (
    <>
      <PageHeader title={t('Fines')} />
      <Row className="g-2 mb-3">
        <Col md={8}>
          <SearchBox
            id="fine-search"
            label={t('Search fines')}
            placeholder={t('Search by member, library card ID or book')}
            value={search}
            onChange={setSearch}
          />
        </Col>
        <Col md={4}>
          <Label for="fine-status" className="visually-hidden">
            {t('Status')}
          </Label>
          <Input id="fine-status" type="select" value={status} onChange={(e) => setStatus(e.target.value)}>
            {STATUSES.map(({ value, label }) => (
              <option key={label} value={value}>
                {t(label)}
              </option>
            ))}
          </Input>
        </Col>
      </Row>
      <ErrorAlert error={action.error} onDismiss={action.clearError} />
      <PagedTable
        list={fines}
        headers={[t('Book'), t('Member'), t('Returned'), t('Late'), t('Fee'), t('Status'), t('Actions')]}
        emptyMessage={status === 'UNPAID' ? t('No unpaid fines.') : t('No fines found.')}
        renderRow={(fine) => (
          <tr key={fine.id}>
            <td className="fw-semibold">{fine.book.title}</td>
            <td>
              {fine.member.name}
              <span className="d-block small text-muted font-monospace">{fine.libraryCardId}</span>
            </td>
            <td className="text-nowrap">{formatDate(fine.returnDate)}</td>
            <td className="text-nowrap">
              {fine.daysLate === 1 ? t('{n} day late', { n: fine.daysLate }) : t('{n} days late', { n: fine.daysLate })}
            </td>
            <td className="text-nowrap fw-semibold">{formatMoney(fine.amount, fine.currency)}</td>
            <td>
              <FineState fine={fine} />
            </td>
            <td className="text-nowrap">
              {fine.status === 'UNPAID' && (
                <ButtonGroup size="sm">
                  <Button color="success" onClick={() => pay(fine)} disabled={action.pending}>
                    {t('Record payment')}
                  </Button>
                  <Button color="secondary" outline onClick={() => waive(fine)} disabled={action.pending}>
                    {t('Waive')}
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
  const t = useT();
  if (fine.status === 'UNPAID') return <span className="stamp stamp-overdue">{t('Unpaid')}</span>;
  const paid = fine.status === 'PAID';
  return (
    <span>
      <span className="stamp stamp-returned">{paid ? t('Paid') : t('Waived')}</span>
      <span className="d-block small text-muted mt-1">
        {paid ? t('Paid by {name}', { name: fine.settledBy }) : t('Waived by {name}', { name: fine.settledBy })}
        {fine.note && <> · {fine.note}</>}
      </span>
    </span>
  );
}
