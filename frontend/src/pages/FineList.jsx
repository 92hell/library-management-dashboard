import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, ButtonGroup, Col, Input, Label, Row } from 'reactstrap';
import { focusPageHeading } from '../a11y';
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
  // Shown for a waive without a reason or a payment amount that can't be right (an Error for ErrorAlert).
  const [missingReason, setMissingReason] = useState(null);
  // The last payment recorded, with a button to print the loan's receipt.
  const [paid, setPaid] = useState(null);
  const navigate = useNavigate();
  const t = useT();

  /** Settling a fine removes it from the Unpaid view, like a deletion, and its buttons with it. */
  async function perform(call) {
    setMissingReason(null);
    setPaid(null);
    let result = null;
    if (await action.run(async () => {
      result = await call();
    })) {
      if (status === 'UNPAID' && result?.status !== 'UNPAID') fines.refreshAfterRemoval();
      else fines.reload();
      focusPageHeading();
    }
    return result;
  }

  /** Records what the member paid now: all that is still due, or part of it. */
  async function pay(fine) {
    const due = fine.outstanding ?? fine.amount;
    const answer = window.prompt(
      t('How much did {name} pay for "{title}"? Still due: {amount}', {
        name: fine.member.name, title: fine.book.title, amount: formatMoney(due, fine.currency),
      }),
      String(due),
    );
    if (answer === null) return;
    const digits = answer.replace(/[^\d]/g, '');
    const amount = Number(digits);
    if (!digits || amount < 1 || amount > due) {
      action.clearError();
      setPaid(null);
      setMissingReason(new Error(t('Enter an amount between 1 and {max}.', { max: formatMoney(due, fine.currency) })));
      return;
    }
    const result = await perform(() => api.post(`/api/fines/${fine.id}/pay`, { amount }));
    if (result) setPaid({ fine: result, amount, name: fine.member.name });
  }

  /** The loan's receipt (it lists every payment) for the member to keep. */
  async function printReceipt(loanId) {
    let receipt = null;
    if (await action.run(async () => {
      receipt = await api.post(`/api/borrowed-books/${loanId}/receipt`);
    })) navigate(`/receipts/${encodeURIComponent(receipt.number)}`);
  }

  function waive(fine) {
    const reason = window.prompt(t('Why waive the late fee for "{title}" ({name})?', { title: fine.book.title, name: fine.member.name }), '');
    if (reason === null) return;
    if (!reason.trim()) {
      action.clearError();
      setMissingReason(new Error(msg('A reason is required to waive a fine.')));
      return;
    }
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
      <div role="status" aria-live="polite">
        {paid && (
          <div className="alert alert-success d-flex flex-wrap gap-2 justify-content-between align-items-center">
            <span>
              {t('Payment of {amount} recorded for {name}.', { amount: formatMoney(paid.amount, paid.fine.currency), name: paid.name })}{' '}
              {paid.fine.outstanding > 0
                ? t('{amount} still due.', { amount: formatMoney(paid.fine.outstanding, paid.fine.currency) })
                : t('The late fee is settled.')}
            </span>
            <Button size="sm" color="success" outline onClick={() => printReceipt(paid.fine.loanId)} disabled={action.pending}>
              {t('Print receipt')}
            </Button>
          </div>
        )}
      </div>
      <ErrorAlert error={missingReason} onDismiss={() => setMissingReason(null)} />
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
            <td className="text-nowrap">
              <span className="fw-semibold">{formatMoney(fine.amount, fine.currency)}</span>
              {fine.status === 'UNPAID' && fine.paidAmount > 0 && (
                <span className="d-block small text-muted">
                  {t('{paid} paid', { paid: formatMoney(fine.paidAmount, fine.currency) })} ·{' '}
                  {t('{amount} still due', { amount: formatMoney(fine.outstanding, fine.currency) })}
                </span>
              )}
            </td>
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
