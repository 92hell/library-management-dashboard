import { useT } from '../i18n';

/** "2 of 3 on shelf" as a library stamp; "On loan" when every copy is out. */
export default function Availability({ book }) {
  const t = useT();
  const total = book.totalCopies ?? 0;
  const available = book.availableCopies ?? 0;
  if (total === 0) return <span className="stamp stamp-cancelled">{t('No copies')}</span>;
  if (available === 0) {
    return (
      <span className="stamp stamp-overdue" title={total === 1 ? t('All {total} copy on loan', { total }) : t('All {total} copies on loan', { total })}>
        {t('On loan')}
      </span>
    );
  }
  return <span className="stamp stamp-active">{t('{available} of {total} on shelf', { available, total })}</span>;
}
