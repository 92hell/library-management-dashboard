import { Button } from 'reactstrap';
import { useT } from '../i18n';

export default function Pager({ page, totalPages, onPageChange }) {
  const t = useT();
  if (totalPages <= 1) return null;
  return (
    <nav className="d-flex align-items-center justify-content-center gap-3" aria-label={t('Pagination')}>
      <Button size="sm" outline disabled={page === 0} onClick={() => onPageChange(page - 1)}>
        ‹ {t('Previous')}
      </Button>
      <span className="text-muted small">
        {t('Page {page} of {total}', { page: page + 1, total: totalPages })}
      </span>
      <Button size="sm" outline disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
        {t('Next')} ›
      </Button>
    </nav>
  );
}
