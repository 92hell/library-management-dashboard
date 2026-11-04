import { Link, useParams } from 'react-router-dom';
import { Button } from 'reactstrap';
import Barcode from '../components/Barcode';
import { ErrorAlert, Loading } from '../components/Feedback';
import { formatDate } from '../format';
import { useApi } from '../hooks';
import { useT } from '../i18n';

/**
 * A library card to print at its real size (85.6 × 54 mm, like a bank card), with the card ID as a
 * barcode for the desk scanner. Librarians print any member's card; members print their own.
 */
export default function CardPrintPage({ own = false }) {
  const { id } = useParams();
  const t = useT();
  const card = useApi(own ? '/api/profile/card' : `/api/members/${id}/card`);
  const back = own ? '/profile' : `/members/${id}`;

  return (
    <>
      <div className="d-print-none">
        <Link to={back} className="btn btn-link px-0 mb-2">
          ← {own ? t('My profile') : t('Member')}
        </Link>
        <h1 className="h3 page-title">{t('Print library card')}</h1>
        <p className="text-muted">
          {t('It prints at its real size, like a bank card. Cut along the dashed line; laminate it to make it last.')}
        </p>
      </div>
      <ErrorAlert error={card.error} />
      {!card.error && !card.data && <Loading />}
      {card.data && (
        <>
          {card.data.cardExpired && (
            <div className="alert alert-warning d-print-none">
              {own
                ? t('Your card expired on {date}. Renew it at the desk first, so the printed date is current.', { date: formatDate(card.data.cardExpiresOn) })
                : t('This card expired on {date}. Renew it first, so the printed date is current.', { date: formatDate(card.data.cardExpiresOn) })}
            </div>
          )}
          <PrintableCard card={card.data} />
          <Button color="primary" className="mt-4 d-print-none" onClick={() => window.print()}>
            {t('Print')}
          </Button>
        </>
      )}
    </>
  );
}

function PrintableCard({ card }) {
  const t = useT();
  return (
    <section className="print-card" aria-label={t('Library card')}>
      <div className="print-card-header">
        <span>{card.libraryName}</span>
        <span>{t('Library card')}</span>
      </div>
      <div className="print-card-body">
        <p className="print-card-name">{card.name}</p>
        <Barcode modules={card.barcode} label={t('Barcode of card {id}', { id: card.libraryCardId })} />
        <p className="print-card-id">{card.libraryCardId}</p>
        <p className="print-card-validity">
          {card.cardExpiresOn ? t('Valid until {date}', { date: formatDate(card.cardExpiresOn) }) : t('Does not expire')}
        </p>
      </div>
    </section>
  );
}
