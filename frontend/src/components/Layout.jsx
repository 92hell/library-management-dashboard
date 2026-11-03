import { Outlet } from 'react-router-dom';
import { Container } from 'reactstrap';
import { NotificationsProvider } from '../notifications';
import AppNavbar from './AppNavbar';

export default function Layout() {
  return (
    <NotificationsProvider>
      <AppNavbar />
      <Container tag="main" className="py-4">
        <Outlet />
      </Container>
    </NotificationsProvider>
  );
}
