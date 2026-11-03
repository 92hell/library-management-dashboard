import { Link } from 'react-router-dom';
import { Button } from 'reactstrap';
import { useAuth } from '../auth';

/** Page title with an "Add" button that only librarians see. */
export default function PageHeader({ title, addLabel, addTo }) {
  const { isLibrarian } = useAuth();
  return (
    <div className="d-flex justify-content-between align-items-center mb-3">
      <h1 className="h3 mb-0">{title}</h1>
      {isLibrarian && addTo && (
        <Button color="success" tag={Link} to={addTo}>
          {addLabel}
        </Button>
      )}
    </div>
  );
}
