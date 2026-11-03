import { Link, useParams } from 'react-router-dom';
import { Button } from 'reactstrap';
import { useAuth } from '../auth';
import { ErrorAlert, Loading } from '../components/Feedback';
import QrCode from '../components/QrCode';
import ReceiptStamp from '../components/ReceiptStamp';
import { EVENT_LABELS, FINE_LABELS, STATUS_LABELS } from '../components/receiptLabels';
import { formatDate, formatMoney, localIsoDate } from '../format';
import { locale, useT } from '../i18n';
import { useApi } from '../hooks';
import ResponsiveTable from '../components/ResponsiveTable';

/** "3 Nov 2026, 15.00" in the reader's time zone. */
export function formatInstant(iso) {
  return iso ? new Date(iso).toLocaleString(locale(), { dateStyle: 'medium', timeStyle: 'short' }) : '—';
}

/** Shown on receipts the public demo issued: they look real, so say plainly that they are not. */
export function DemoWarning() {
  const t = useT();
  return (
    <div className="alert alert-warning fw-semibold">
      {t('Demo receipt — not a real loan record. This app is a public demo.')}
    </div>
  );
}

/** A loan receipt, laid out to be printed (or downloaded as PDF) and kept as evidence. */
export default function ReceiptPage() {
  const { number } = useParams();
  const { isLibrarian } = useAuth();
  const t = useT();
  const receipt = useApi(`/api/receipts/${encodeURIComponent(number)}`);

  if (receipt.error) return <ErrorAlert error={receipt.error} />;
  if (!receipt.data) return <Loading />;
  const r = receipt.data;
  const { loan, events, fine, library, demo } = r.content;
  const rows = [
    [t('Book'), loan.bookTitle],
    [t('Author'), loan.authorName],
    [t('Copy'), loan.copyBarcode],
    [t('Member'), loan.memberName],
    [t('Library card ID'), loan.libraryCardId],
    [t('Status'), t(STATUS_LABELS[loan.status] ?? loan.status)],
    [t('Borrowed'), formatDate(loan.borrowDate)],
    [t('Due'), formatDate(loan.dueDate)],
    [t('Returned on'), formatDate(loan.returnDate)],
    [t('Renewals'), String(loan.renewals)],
  ];

  return (
    <>
      <div className="d-flex flex-wrap gap-2 mb-3 d-print-none">
        <Link to={isLibrarian ? '/borrowed-books' : '/my/loans'} className="btn btn-link px-0 me-auto">
          ← {isLibrarian ? t('Loans') : t('My loans')}
        </Link>
        <Button color="primary" onClick={() => window.print()}>
          {t('Print')}
        </Button>
        <a className="btn btn-outline-primary" href={`/api/receipts/${encodeURIComponent(r.number)}.pdf`} download>
          {t('Download PDF')}
        </a>
      </div>
      <article className="receipt">
        {demo && <DemoWarning />}
        <header className="d-flex justify-content-between align-items-start gap-3 mb-3">
          <div>
            <p className="receipt-library mb-1">{library}</p>
            <h1 className="h4 mb-1">{t('Loan receipt')}</h1>
            <p className="small text-muted mb-0">
              <span className="font-monospace">{r.number}</span> · {t('Issued {date} by {name}', { date: formatInstant(r.issuedAt), name: r.issuedBy })}
            </p>
          </div>
          <QrCode qr={r.qr} />
        </header>
        <div className="d-flex flex-wrap gap-4 align-items-start">
          <dl className="receipt-details flex-grow-1 mb-0">
            {rows.map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value || '—'}</dd>
              </div>
            ))}
            {fine && (
              <div>
                <dt>{t('Late fee')}</dt>
                <dd>
                  {formatMoney(fine.amount, fine.currency)} · {t(fine.daysLate === 1 ? '{n} day late' : '{n} days late', { n: fine.daysLate })} ·{' '}
                  {t(FINE_LABELS[fine.status] ?? fine.status)}
                </dd>
              </div>
            )}
          </dl>
          <ReceiptStamp library={library} date={formatDate(localIsoDate(r.issuedAt))} />
        </div>
        <h2 id="receipt-history" className="h6 mt-4">
          {t('History')}
        </h2>
        <ResponsiveTable size="sm" className="receipt-history" aria-labelledby="receipt-history">
          <thead>
            <tr>
              <th scope="col">{t('When')}</th>
              <th scope="col">{t('What happened')}</th>
              <th scope="col">{t('By')}</th>
              <th scope="col">{t('Details')}</th>
            </tr>
          </thead>
          <tbody>
            {events.map((e, i) => (
              <tr key={i}>
                <td className="text-nowrap">{formatInstant(e.occurredAt)}</td>
                <td>{t(EVENT_LABELS[e.type] ?? e.type)}</td>
                <td>{e.actor}</td>
                <td className="small">{e.detail}</td>
              </tr>
            ))}
          </tbody>
        </ResponsiveTable>
        <footer className="receipt-signature small">
          <p className="mb-1">
            {t('Digitally signed with {algorithm} (key {keyId}). SHA-256 of the signed record:', { algorithm: r.algorithm, keyId: r.keyId })}
          </p>
          <p className="font-monospace text-break mb-1">{r.sha256}</p>
          <p className="mb-1">
            {t('Check this receipt:')} <span className="text-break">{r.verifyUrl}</span>
          </p>
          <p className="text-muted mb-0">
            {t('The signature proves the library issued this receipt and that it has not been changed since. It is not a certified electronic signature (PSrE).')}
          </p>
        </footer>
      </article>
    </>
  );
}
