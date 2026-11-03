import { useState } from 'react';
import { api } from '../api';
import { ErrorAlert } from '../components/Feedback';
import PagedTable from '../components/PagedTable';
import PageHeader from '../components/PageHeader';
import RowActions from '../components/RowActions';
import SearchBox from '../components/SearchBox';
import { formatDate } from '../format';
import { useAction, useDebouncedValue, usePagedList } from '../hooks';

export default function AuthorList() {
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const authors = usePagedList('/api/authors', { q });
  const removal = useAction();

  async function remove(author) {
    if (!window.confirm(`Delete author "${author.name}"?`)) return;
    if (await removal.run(() => api.delete(`/api/authors/${author.id}`))) authors.refreshAfterRemoval();
  }

  return (
    <>
      <PageHeader title="Authors" addLabel="Add author" addTo="/authors/new" />
      <div className="mb-3">
        <SearchBox id="author-search" label="Search authors by name" value={search} onChange={setSearch} />
      </div>
      <ErrorAlert error={removal.error} onDismiss={removal.clearError} />
      <PagedTable
        list={authors}
        headers={['Name', 'Date of birth', 'Actions']}
        emptyMessage="No authors found."
        renderRow={(author) => (
          <tr key={author.id}>
            <td className="fw-semibold">{author.name}</td>
            <td>{formatDate(author.dateOfBirth)}</td>
            <td>
              <RowActions to={`/authors/${author.id}`} onDelete={() => remove(author)} disabled={removal.pending} />
            </td>
          </tr>
        )}
      />
    </>
  );
}
