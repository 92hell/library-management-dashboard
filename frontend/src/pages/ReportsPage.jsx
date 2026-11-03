import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ButtonGroup, Card, CardBody, Col, Input, Label, Row } from 'reactstrap';
import { buildUrl } from '../api';
import { ErrorAlert, Loading } from '../components/Feedback';
import { formatDate, formatDateParts, formatMoney, formatNumber, todayIso } from '../format';
import { useApi, useDebouncedValue } from '../hooks';
import { msg, useT } from '../i18n';
import ColumnChart from '../components/ColumnChart';
import ResponsiveTable from '../components/ResponsiveTable';

/** "YYYY-MM-DD" for `days` days before today. */
function daysAgoIso(days) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const PRESETS = [
  { label: msg('Last 30 days'), from: () => daysAgoIso(29) },
  { label: msg('Last 90 days'), from: () => daysAgoIso(89) },
  { label: msg('This year'), from: () => `${new Date().getFullYear()}-01-01` },
];

/** Librarian reports for a date range, with CSV downloads of the same range. */
export default function ReportsPage() {
  const [range, setRange] = useState({ from: daysAgoIso(29), to: todayIso() });
  const params = useDebouncedValue(range);
  const report = useApi('/api/reports/summary', params);
  const r = report.data;
  const t = useT();

  return (
    <>
      <h1 className="h3 mb-1 page-title">{t('Reports')}</h1>
      <p className="page-subtitle mb-3">
        {r ? `${formatDate(r.from)} – ${formatDate(r.to)}` : t('Lending, late returns, members and fines')}
      </p>
      <Row className="g-2 align-items-end mb-4">
        <Col xs={6} md={3}>
          <Label for="report-from" className="small mb-1">
            {t('From')}
          </Label>
          <Input id="report-from" type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} />
        </Col>
        <Col xs={6} md={3}>
          <Label for="report-to" className="small mb-1">
            {t('To')}
          </Label>
          <Input id="report-to" type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} />
        </Col>
        <Col md={6}>
          <ButtonGroup size="sm">
            {PRESETS.map((p) => (
              <Button key={p.label} color="secondary" outline onClick={() => setRange({ from: p.from(), to: todayIso() })}>
                {t(p.label)}
              </Button>
            ))}
          </ButtonGroup>
        </Col>
      </Row>
      <ErrorAlert error={report.error} />
      {!r ? (
        !report.error && <Loading />
      ) : (
        <div className={report.loading ? 'opacity-50' : ''} aria-busy={report.loading}>
          <Row xs="2" lg="5" className="g-3 mb-4">
            <Tile label={t('Books lent')} value={formatNumber(r.loans)} />
            <Tile label={t('Returned')} value={formatNumber(r.returns)} />
            <Tile
              label={t('Returned late')}
              value={r.lateRate == null ? '—' : `${Math.round(r.lateRate * 100)}%`}
              note={
                r.lateRate == null
                  ? t('Nothing fell due')
                  : t('{late} of {due} loans that fell due', { late: formatNumber(r.lateInPeriod), due: formatNumber(r.dueInPeriod) })
              }
            />
            <Tile
              label={t('Active members')}
              value={formatNumber(r.activeMembers)}
              note={t('{n} new', { n: formatNumber(r.newMembers) })}
            />
            <Tile
              label={t('Fines charged')}
              value={formatMoney(r.finesCharged, r.currency)}
              note={t('{amount} collected', { amount: formatMoney(r.finesCollected, r.currency) })}
              small
            />
          </Row>
          <Trends report={r} />
          <Row className="g-4">
            <Col lg={7}>
              <Ranking
                id="most-borrowed"
                title={t('Most borrowed')}
                empty={t('No books were lent in this period.')}
                headers={['#', msg('Title'), msg('Author'), msg('Loans')]}
              >
                {r.mostBorrowed.map((book, i) => (
                  <tr key={book.bookId}>
                    <td className="text-muted">{i + 1}</td>
                    <td>
                      <Link to={`/catalogue/${book.bookId}`} className="fw-semibold">
                        {book.title}
                      </Link>
                    </td>
                    <td>{book.authorName}</td>
                    <td className="text-end">{formatNumber(book.loans)}</td>
                  </tr>
                ))}
              </Ranking>
            </Col>
            <Col lg={5}>
              <Ranking
                id="most-active"
                title={t('Most active members')}
                empty={t('Nobody borrowed in this period.')}
                headers={['#', msg('Member'), msg('Loans')]}
              >
                {r.mostActiveMembers.map((m, i) => (
                  <tr key={m.memberId}>
                    <td className="text-muted">{i + 1}</td>
                    <td>
                      <Link to={`/members/${m.memberId}`}>{m.name}</Link>
                      <span className="d-block small text-muted font-monospace">{m.libraryCardId}</span>
                    </td>
                    <td className="text-end">{formatNumber(m.loans)}</td>
                  </tr>
                ))}
              </Ranking>
            </Col>
          </Row>
          <div className="d-flex flex-wrap gap-2 mt-2">
            <a className="btn btn-outline-primary" href={buildUrl('/api/reports/loans.csv', params)} download>
              {t('Download loans (CSV)')}
            </a>
            <a className="btn btn-outline-primary" href={buildUrl('/api/reports/most-borrowed.csv', params)} download>
              {t('Download most borrowed (CSV)')}
            </a>
          </div>
        </div>
      )}
    </>
  );
}

/** "Nov 3 – Nov 9, 2026" (both ends shown, as the first and last bucket may be cut to the range). */
function period(bucket) {
  return `${formatDate(bucket.start)} – ${formatDate(bucket.end)}`;
}

/** Books lent per week and returns per month (on time / late) over the report's range. */
function Trends({ report }) {
  const t = useT();
  const spansYears = report.from.slice(0, 4) !== report.to.slice(0, 4);
  const weeks = report.weekly.map((w) => ({
    key: w.start, label: formatDateParts(w.start, { month: 'short', day: 'numeric' }), title: period(w), values: [w.loans],
  }));
  const months = report.monthly.map((m) => ({
    key: m.start,
    label: formatDateParts(m.start, spansYears ? { month: 'short', year: '2-digit' } : { month: 'short' }),
    title: period(m),
    values: [m.onTime, m.late],
  }));
  return (
    <Row className="g-4 mb-4">
      <Col lg={7}>
        <Card className="h-100 shadow-sm">
          <CardBody>
            <ColumnChart title={t('Books lent per week')} buckets={weeks}
              series={[{ name: t('Books lent'), color: 'var(--chart-1)' }]} />
          </CardBody>
        </Card>
      </Col>
      <Col lg={5}>
        <Card className="h-100 shadow-sm">
          <CardBody>
            <ColumnChart title={t('Returns per month')} buckets={months}
              series={[{ name: t('Returned on time'), color: 'var(--chart-1)' }, { name: t('Returned late'), color: 'var(--chart-2)' }]} />
          </CardBody>
        </Card>
      </Col>
    </Row>
  );
}

function Tile({ label, value, note, small }) {
  return (
    <Col>
      <Card className="h-100 shadow-sm tile">
        <CardBody>
          <span className={`d-block fw-semibold ${small ? 'h4 my-2' : 'display-6'}`}>{value}</span>
          <span className="small d-block">{label}</span>
          {note && <span className="small text-muted d-block">{note}</span>}
        </CardBody>
      </Card>
    </Col>
  );
}

/** `headers` are untranslated labels (msg), shown with t(). */
function Ranking({ id, title, headers, empty, children }) {
  const t = useT();
  return (
    <section aria-labelledby={`${id}-heading`}>
      <h2 id={`${id}-heading`} className="h5">
        {title}
      </h2>
      {children.length === 0 ? (
        <p className="empty-state">{empty}</p>
      ) : (
        <ResponsiveTable responsive className="align-middle shadow-sm" aria-labelledby={`${id}-heading`}>
          <thead className="table-library">
            <tr>
              {headers.map((h) => (
                <th key={h} scope="col" className={h === 'Loans' ? 'text-end' : undefined}>
                  {t(h)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>{children}</tbody>
        </ResponsiveTable>
      )}
    </section>
  );
}
