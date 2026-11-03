import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth';
import { Loading } from './Feedback';

/** Renders its children for signed-in users and sends everyone else to /login. */
export default function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <Loading label="Checking your session…" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  return children;
}
