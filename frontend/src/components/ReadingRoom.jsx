import { Card, CardBody, Container } from 'reactstrap';
import Bookshelf from './Bookshelf';
import LanguageSwitch from './LanguageSwitch';

/** The signed-out pages' frame: the bookshelf, then a card with a title and the language switch. */
export default function ReadingRoom({ title, children }) {
  return (
    <div className="reading-room">
      <Container className="py-5" style={{ maxWidth: '28rem' }}>
        <Bookshelf />
        <Card className="shadow-sm">
          <CardBody className="p-4">
            <div className="d-flex justify-content-between align-items-center gap-2 mb-3">
              <h1 className="h5 mb-0">{title}</h1>
              <LanguageSwitch />
            </div>
            {children}
          </CardBody>
        </Card>
      </Container>
    </div>
  );
}
