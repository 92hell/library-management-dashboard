import { Button } from 'reactstrap';

export default function Pager({ page, totalPages, onPageChange }) {
  if (totalPages <= 1) return null;
  return (
    <nav className="d-flex align-items-center justify-content-center gap-3" aria-label="Pagination">
      <Button size="sm" outline disabled={page === 0} onClick={() => onPageChange(page - 1)}>
        ‹ Previous
      </Button>
      <span className="text-muted small">
        Page {page + 1} of {totalPages}
      </span>
      <Button size="sm" outline disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
        Next ›
      </Button>
    </nav>
  );
}
