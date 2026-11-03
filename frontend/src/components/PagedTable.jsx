import { Table } from 'reactstrap';
import { ErrorAlert, Loading } from './Feedback';
import Pager from './Pager';

/**
 * Renders a list returned by usePagedList: loading and error states, an empty message,
 * the table itself and the pager.
 */
export default function PagedTable({ list, headers, renderRow, emptyMessage }) {
  const { data, error, loading, page, setPage } = list;

  if (error) return <ErrorAlert error={error} />;
  if (!data) return <Loading />;
  if (data.content.length === 0) {
    return loading ? <Loading /> : <p className="empty-state">{emptyMessage}</p>;
  }

  return (
    <>
      <Table responsive hover className={`align-middle shadow-sm ${loading ? 'opacity-50' : ''}`} aria-busy={loading}>
        <thead className="table-library">
          <tr>
            {headers.map((header) => (
              <th key={header} scope="col">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{data.content.map(renderRow)}</tbody>
      </Table>
      <div className="d-flex flex-column flex-sm-row align-items-center justify-content-between gap-2">
        <span className="text-muted small">{data.totalElements} total</span>
        <Pager page={page} totalPages={data.totalPages} onPageChange={setPage} />
      </div>
    </>
  );
}
