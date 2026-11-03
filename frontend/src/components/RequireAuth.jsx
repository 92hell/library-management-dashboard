import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth';
import { useT } from '../i18n';
import { Loading } from './Feedback';

/**
 * Renders its children for signed-in users and sends everyone else to /login, remembering the page
 * to return to - except after an explicit logout.
 */
export default function RequireAuth({ children }) {
  const { user, loading, loggedOut } = useAuth();
  const location = useLocation();
  const t = useT();

  if (loading) return <Loading label={t('Checking your session…')} />;
  if (!user) return <Navigate to="/login" replace state={loggedOut ? undefined : { from: location }} />;
  return children;
}
