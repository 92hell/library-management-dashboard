import { useApi } from '../hooks';

/** A strip on every page while the app runs as a public demo. */
export default function DemoBanner() {
  const { data: demo } = useApi('/api/auth/demo');
  if (!demo?.enabled) return null;
  return (
    <div className="demo-banner" role="note">
      This is a public demo: please don&apos;t enter real personal data. Everything resets every night.
    </div>
  );
}
