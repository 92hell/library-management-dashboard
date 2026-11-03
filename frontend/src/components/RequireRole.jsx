import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth';

/** Renders its children only for users with `role`; everyone else goes to their home page. */
export default function RequireRole({ role, children }) {
  const { user } = useAuth();
  if (user?.role !== role) return <Navigate to="/" replace />;
  return children;
}
