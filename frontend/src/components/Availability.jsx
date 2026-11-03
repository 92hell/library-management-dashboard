/** "2 of 3 on shelf" as a library stamp; "On loan" when every copy is out. */
export default function Availability({ book }) {
  const total = book.totalCopies ?? 0;
  const available = book.availableCopies ?? 0;
  if (total === 0) return <span className="stamp stamp-cancelled">No copies</span>;
  if (available === 0) {
    return (
      <span className="stamp stamp-overdue" title={`All ${total} ${total === 1 ? 'copy' : 'copies'} on loan`}>
        On loan
      </span>
    );
  }
  return <span className="stamp stamp-active">{`${available} of ${total} on shelf`}</span>;
}
