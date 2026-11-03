import { useAuth } from '../auth';
import CustomerHome from './CustomerHome';
import LibrarianHome from './LibrarianHome';

export default function Home() {
  const { isLibrarian } = useAuth();
  return isLibrarian ? <LibrarianHome /> : <CustomerHome />;
}
