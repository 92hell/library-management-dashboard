import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge, Button, Input, Label } from 'reactstrap';
import { api } from '../api';
import { formatDate, formatInstant } from '../format';
import { useAction, useApi } from '../hooks';
import { msg, useT } from '../i18n';
import { ErrorAlert } from './Feedback';

/** "Valid until 3 Nov 2027", "Expired on …" or "Does not expire", for librarians and members. */
export function CardValidity({ member }) {
  const t = useT();
  if (!member.cardExpiresOn) return <span className="text-muted">{t('Does not expire')}</span>;
  if (member.cardExpired) {
    return <Badge color="danger">{t('Expired on {date}', { date: formatDate(member.cardExpiresOn) })}</Badge>;
  }
  return <span>{t('Valid until {date}', { date: formatDate(member.cardExpiresOn) })}</span>;
}

/**
 * The member's library card at the desk: renew it, replace a lost one (the old ID is kept in the
 * history), and merge a duplicate record of the same person into this one.
 */
export default function MemberCardPanel({ member, onChanged }) {
  const history = useApi(`/api/members/${member.id}/cards`);
  const action = useAction();
  const [done, setDone] = useState(null);
  const [missingReason, setMissingReason] = useState(null);
  const t = useT();

  async function perform(call, message) {
    setDone(null);
    setMissingReason(null);
    let result = null;
    if (await action.run(async () => {
      result = await call();
    })) {
      setDone(message(result));
      history.reload();
      onChanged();
    }
  }

  function renew() {
    perform(
      () => api.post(`/api/members/${member.id}/card/renew`),
      (updated) => (updated.cardExpiresOn
        ? t('Card renewed. Valid until {date}.', { date: formatDate(updated.cardExpiresOn) })
        : t('Card renewed.')),
    );
  }

  function replace() {
    const reason = window.prompt(t('Why is the card {id} being replaced (for example lost or damaged)?', { id: member.libraryCardId }), '');
    if (reason === null) return;
    if (!reason.trim()) {
      action.clearError();
      setDone(null);
      setMissingReason(new Error(msg('A reason is required to replace a card.')));
      return;
    }
    const old = member.libraryCardId;
    perform(
      () => api.post(`/api/members/${member.id}/card/replace`, { reason: reason.trim() }),
      (updated) => t('New card {id} issued. The old card {old} no longer works.', { id: updated.libraryCardId, old }),
    );
  }

  return (
    <section className="p-4 border rounded shadow-sm" aria-labelledby="card-heading">
      <h2 id="card-heading" className="h5 mb-3">{t('Library card')}</h2>
      <p className="mb-1">
        <span className="font-monospace fw-semibold me-2">{member.libraryCardId}</span>
        <CardValidity member={member} />
      </p>
      <div className="d-flex flex-wrap gap-2 my-3">
        <Button color="primary" outline size="sm" onClick={renew} disabled={action.pending}>
          {t('Renew card')}
        </Button>
        <Button color="secondary" outline size="sm" onClick={replace} disabled={action.pending}>
          {t('Replace card')}
        </Button>
        <Link to={`/members/${member.id}/card`} className="btn btn-outline-secondary btn-sm">
          {t('Print card')}
        </Link>
      </div>
      <div role="status" aria-live="polite">
        {done && <div className="alert alert-success">{done}</div>}
      </div>
      <ErrorAlert error={missingReason} onDismiss={() => setMissingReason(null)} />
      <ErrorAlert error={action.error} onDismiss={action.clearError} />
      <CardHistory cards={history.data} />
      <MergeDuplicate member={member} onMerged={(name) => {
        setDone(t('{name} was merged into this member.', { name }));
        history.reload();
        onChanged();
      }} />
    </section>
  );
}

function CardHistory({ cards }) {
  const t = useT();
  if (!cards?.length) return null;
  return (
    <>
      <h3 id="earlier-cards" className="h6 mt-3">{t('Earlier cards')}</h3>
      <ul className="list-unstyled small mb-0" aria-labelledby="earlier-cards">
        {cards.map((card) => (
          <li key={card.libraryCardId} className="mb-1">
            <span className="font-monospace">{card.libraryCardId}</span>{' '}
            {card.reason === 'MERGED'
              ? t('merged from {name}', { name: card.note })
              : t('replaced: {reason}', { reason: card.note })}{' '}
            <span className="text-muted">
              · {t('{date} by {name}', { date: formatInstant(card.retiredAt), name: card.retiredBy })}
            </span>
          </li>
        ))}
      </ul>
    </>
  );
}

function MergeDuplicate({ member, onMerged }) {
  const options = useApi('/api/members/options');
  const [duplicateId, setDuplicateId] = useState('');
  const merge = useAction();
  const t = useT();
  const others = (options.data ?? []).filter((option) => option.id !== member.id);

  async function submit(event) {
    event.preventDefault();
    const duplicate = others.find((option) => String(option.id) === duplicateId);
    if (!duplicate) return;
    if (!window.confirm(t('Merge "{duplicate}" into "{name}"? Their loans, fines, receipts and online account move to {name}, and the other record is removed. This cannot be undone.', {
      duplicate: duplicate.name, name: member.name,
    }))) return;
    if (await merge.run(() => api.post(`/api/members/${member.id}/merge`, { duplicateId: duplicate.id }))) {
      setDuplicateId('');
      options.reload();
      onMerged(duplicate.name);
    }
  }

  return (
    <form className="mt-4" onSubmit={submit}>
      <h3 className="h6">{t('Merge a duplicate record')}</h3>
      <p className="small text-muted">
        {t('If this person also has another member record (for example they registered online without their card), merge it into this one.')}
      </p>
      <ErrorAlert error={merge.error} onDismiss={merge.clearError} />
      <div className="d-flex flex-wrap gap-2 align-items-end">
        <div className="flex-grow-1">
          <Label for="merge-duplicate" className="small">{t('Duplicate record')}</Label>
          <Input id="merge-duplicate" type="select" value={duplicateId} onChange={(e) => setDuplicateId(e.target.value)}>
            <option value="">{t('Choose a member…')}</option>
            {others.map((option) => (
              <option key={option.id} value={option.id}>
                {option.name}
              </option>
            ))}
          </Input>
        </div>
        <Button type="submit" color="danger" outline disabled={!duplicateId || merge.pending}>
          {t('Merge into this member')}
        </Button>
      </div>
    </form>
  );
}
