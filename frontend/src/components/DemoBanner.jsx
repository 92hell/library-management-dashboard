import { useApi } from '../hooks';
import { useT } from '../i18n';

/** A strip on every page while the app runs as a public demo. */
export default function DemoBanner() {
  const { data: demo } = useApi('/api/auth/demo');
  const t = useT();
  if (!demo?.enabled) return null;
  return (
    <div className="demo-banner" role="note">
      {t('This is a public demo: please don\'t enter real personal data. Everything resets every night.')}
    </div>
  );
}
