import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ButtonGroup, Card, CardBody, Col, Input, Label, Row, Table } from 'reactstrap';
import { buildUrl } from '../api';
import { ErrorAlert, Loading } from '../components/Feedback';
import { formatDate, formatMoney, todayIso } from '../format';
import { useApi, useDebouncedValue } from '../hooks';

/** "YYYY-MM-DD" for `days` days before today. */
function daysAgoIso(days) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const PRESETS = [
  { label: 'Last 30 days', from: () => daysAgoIso(29) },
  { label: 'Last 90 days', from: () => daysAgoIso(89) },
  { label: 'This year', from: () => `${new Date().getFullYear()}-01-01` },
];

/** Librarian reports for a date range, with CSV downloads of the same range. */
export default function ReportsPage() {
  const [range, setRange] = useState({ from: daysAgoIso(29), to: todayIso() });
  const params = useDebouncedValue(range);
  const report = useApi('/api/reports/summary', params);
  const r = report.data;

  return (
    <>
      <h1 className="h3 mb-1 page-title">Reports</h1>
      <p className="page-subtitle mb-3">
        {r ? `${formatDate(r.from)} – ${formatDate(r.to)}` : 'Lending, late returns, members and fines'}
      </p>
      <Row className="g-2 align-items-end mb-4">
        <Col xs={6} md={3}>
          <Label for="report-from" className="small mb-1">
            From
          </Label>
          <Input id="report-from" type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} />
        </Col>
        <Col xs={6} md={3}>
          <Label for="report-to" className="small mb-1">
            To
          </Label>
          <Input id="report-to" type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} />
        </Col>
        <Col md={6}>
          <ButtonGroup size="sm">
            {PRESETS.map((p) => (
              <Button key={p.label} color="secondary" outline onClick={() => setRange({ from: p.from(), to: todayIso() })}>
                {p.label}
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
            <Tile label="Books lent" value={r.loans} />
            <Tile label="Returned" value={r.returns} />
            <Tile
              label="Returned late"
              value={r.lateRate == null ? '—' : `${Math.round(r.lateRate * 100)}%`}
              note={r.lateRate == null ? 'Nothing fell due' : `${r.lateInPeriod} of ${r.dueInPeriod} loans that fell due`}
            />
            <Tile label="Active members" value={r.activeMembers} note={`${r.newMembers} new`} />
            <Tile
              label="Fines charged"
              value={formatMoney(r.finesCharged, r.currency)}
              note={`${formatMoney(r.finesCollected, r.currency)} collected`}
              small
            />
          </Row>
          <Row className="g-4">
            <Col lg={7}>
              <Ranking id="most-borrowed" title="Most borrowed" empty="No books were lent in this period." headers={['#', 'Title', 'Author', 'Loans']}>
                {r.mostBorrowed.map((t, i) => (
                  <tr key={t.bookId}>
                    <td className="text-muted">{i + 1}</td>
                    <td>
                      <Link to={`/catalogue/${t.bookId}`} className="fw-semibold">
                        {t.title}
                      </Link>
                    </td>
                    <td>{t.authorName}</td>
                    <td className="text-end">{t.loans}</td>
                  </tr>
                ))}
              </Ranking>
            </Col>
            <Col lg={5}>
              <Ranking id="most-active" title="Most active members" empty="Nobody borrowed in this period." headers={['#', 'Member', 'Loans']}>
                {r.mostActiveMembers.map((m, i) => (
                  <tr key={m.memberId}>
                    <td className="text-muted">{i + 1}</td>
                    <td>
                      <Link to={`/members/${m.memberId}`}>{m.name}</Link>
                      <span className="d-block small text-muted font-monospace">{m.libraryCardId}</span>
                    </td>
                    <td className="text-end">{m.loans}</td>
                  </tr>
                ))}
              </Ranking>
            </Col>
          </Row>
          <div className="d-flex flex-wrap gap-2 mt-2">
            <a className="btn btn-outline-primary" href={buildUrl('/api/reports/loans.csv', params)} download>
              Download loans (CSV)
            </a>
            <a className="btn btn-outline-primary" href={buildUrl('/api/reports/most-borrowed.csv', params)} download>
              Download most borrowed (CSV)
            </a>
          </div>
        </div>
      )}
    </>
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

function Ranking({ id, title, headers, empty, children }) {
  return (
    <section aria-labelledby={`${id}-heading`}>
      <h2 id={`${id}-heading`} className="h5">
        {title}
      </h2>
      {children.length === 0 ? (
        <p className="empty-state">{empty}</p>
      ) : (
        <Table responsive className="align-middle shadow-sm" aria-labelledby={`${id}-heading`}>
          <thead className="table-library">
            <tr>
              {headers.map((h) => (
                <th key={h} scope="col" className={h === 'Loans' ? 'text-end' : undefined}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>{children}</tbody>
        </Table>
      )}
    </section>
  );
}
