import { Fragment, useState } from 'react';
import { Badge, Button } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert } from '../components/Feedback';
import MemberLoans from '../components/MemberLoans';
import PagedTable from '../components/PagedTable';
import PageHeader from '../components/PageHeader';
import RowActions from '../components/RowActions';
import SearchBox from '../components/SearchBox';
import { useAction, useDebouncedValue, usePagedList } from '../hooks';

const COLUMN_COUNT = 8;

export default function MemberList() {
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const members = usePagedList('/api/members', { q });
  const [expandedId, setExpandedId] = useState(null);
  const removal = useAction();

  async function remove(member) {
    if (!window.confirm(`Delete member "${member.name}"?`)) return;
    if (await removal.run(() => api.delete(`/api/members/${member.id}`))) members.refreshAfterRemoval();
  }

  function toggleLoans(memberId) {
    setExpandedId((current) => (current === memberId ? null : memberId));
  }

  return (
    <>
      <PageHeader title="Members" addLabel="Add member" addTo="/members/new" />
      <div className="mb-3">
        <SearchBox
          id="member-search"
          label="Search members"
          placeholder="Search by name, library card ID, email or phone"
          value={search}
          onChange={setSearch}
        />
      </div>
      <ErrorAlert error={removal.error} onDismiss={removal.clearError} />
      <PagedTable
        list={members}
        headers={['Name', 'Library card ID', 'Email', 'Phone', 'Online account', 'Status', 'Loans', 'Actions']}
        emptyMessage="No members found."
        renderRow={(member) => {
          const expanded = expandedId === member.id;
          return (
            <Fragment key={member.id}>
              <tr>
                <td className="fw-semibold">{member.name}</td>
                <td className="text-nowrap font-monospace">{member.libraryCardId}</td>
                <td>{member.email}</td>
                <td className="text-nowrap">{member.phone}</td>
                <td>
                  {member.accountUsername ? (
                    <Badge color="info">{member.accountUsername}</Badge>
                  ) : (
                    <span className="small text-muted">Walk-in</span>
                  )}
                </td>
                <td>
                  <Badge color={member.active ? 'success' : 'secondary'}>{member.active ? 'Active' : 'Inactive'}</Badge>
                </td>
                <td>
                  <Button
                    size="sm"
                    color="link"
                    className="p-0"
                    onClick={() => toggleLoans(member.id)}
                    aria-expanded={expanded}
                  >
                    {expanded ? 'Hide' : 'Show'}
                  </Button>
                </td>
                <td>
                  <RowActions to={`/members/${member.id}`} onDelete={() => remove(member)} disabled={removal.pending} />
                </td>
              </tr>
              {expanded && (
                <tr>
                  <td colSpan={COLUMN_COUNT} className="bg-body-tertiary">
                    <MemberLoans memberId={member.id} flush />
                  </td>
                </tr>
              )}
            </Fragment>
          );
        }}
      />
    </>
  );
}
