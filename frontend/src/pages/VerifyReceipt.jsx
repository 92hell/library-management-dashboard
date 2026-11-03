import { useParams, useSearchParams } from 'react-router-dom';
import { Alert, Container, Table } from 'reactstrap';
import { ErrorAlert, Loading } from '../components/Feedback';
import LanguageSwitch from '../components/LanguageSwitch';
import { EVENT_LABELS, FINE_LABELS, STATUS_LABELS } from '../components/receiptLabels';
import { formatDate, formatMoney } from '../format';
import { useT } from '../i18n';
import { useApi } from '../hooks';
import { formatInstant } from './ReceiptPage';

/** Public page behind a receipt's QR code: is it genuine, and what does the library's record say? */
export default function VerifyReceipt() {
  const { number } = useParams();
  const [params] = useSearchParams();
  const t = useT();
  const result = useApi(`/api/receipts/verify/${number}`, { code: params.get('code') ?? '' });
  const v = result.data;

  return (
    <Container tag="main" className="py-4" style={{ maxWidth: '46rem' }}>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h4 mb-0">{t('Check a loan receipt')}</h1>
        <LanguageSwitch />
      </div>
      <ErrorAlert error={result.error} />
      {!v && !result.error && <Loading />}
      {v && (
        <>
          {v.genuine ? (
            <div className="alert alert-success" role="status">
              <strong>{t('Genuine receipt')}</strong> — {t('issued by {library} and unchanged since.', { library: v.library })}
            </div>
          ) : (
            <Alert color="danger" role="alert">
              <strong>{t('Warning')}</strong> — {t('This receipt does not match its signature. Do not accept it as evidence; ask the library.')}
            </Alert>
          )}
          <dl className="receipt-details">
            {[
              [t('Receipt number'), v.number],
              [t('Issued'), formatInstant(v.issuedAt)],
              [t('Book'), v.bookTitle],
              [t('Copy'), v.copyBarcode],
              [t('Member'), v.memberName],
              [t('Status'), t(STATUS_LABELS[v.status] ?? v.status)],
              [t('Borrowed'), formatDate(v.borrowDate)],
              [t('Due'), formatDate(v.dueDate)],
              [t('Returned on'), formatDate(v.returnDate)],
            ].map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value || '—'}</dd>
              </div>
            ))}
            {v.fine && (
              <div>
                <dt>{t('Late fee')}</dt>
                <dd>
                  {formatMoney(v.fine.amount, v.fine.currency)} · {t(FINE_LABELS[v.fine.status] ?? v.fine.status)}
                </dd>
              </div>
            )}
          </dl>
          <h2 id="verify-history" className="h6">
            {t('History')}
          </h2>
          <Table size="sm" aria-labelledby="verify-history">
            <tbody>
              {v.events.map((e, i) => (
                <tr key={i}>
                  <td className="text-nowrap">{formatInstant(e.occurredAt)}</td>
                  <td>{t(EVENT_LABELS[e.type] ?? e.type)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
          <p className="small text-muted">
            {t('Signature: {algorithm}, key {keyId}.', { algorithm: v.algorithm, keyId: v.keyId })}{' '}
            <a href="/api/receipts/public-key">{t('Public key')}</a>
          </p>
        </>
      )}
    </Container>
  );
}
