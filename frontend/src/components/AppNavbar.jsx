import { useState } from 'react';
import { Link, NavLink as RouterNavLink } from 'react-router-dom';
import { Badge, Button, Collapse, Nav, Navbar, NavbarBrand, NavbarToggler, NavItem, NavLink } from 'reactstrap';
import { useAuth } from '../auth';
import { useNotifications } from '../notifications';
import BrandMark from './BrandMark';

const SECTIONS = {
  LIBRARIAN: [
    { to: '/borrowed-books', label: 'Loans' },
    { to: '/books', label: 'Books' },
    { to: '/authors', label: 'Authors' },
    { to: '/members', label: 'Members' },
    { to: '/fines', label: 'Fines' },
    { to: '/users', label: 'Users' },
  ],
  CUSTOMER: [
    { to: '/catalogue', label: 'Catalogue' },
    { to: '/my/loans', label: 'My loans' },
  ],
};

export default function AppNavbar() {
  const { user, isLibrarian, logout } = useAuth();
  const { unread } = useNotifications();
  const [isOpen, setIsOpen] = useState(false);
  const close = () => setIsOpen(false);

  return (
    <Navbar dark expand="md" className="px-3 library-nav">
      <NavbarBrand tag={Link} to="/">
        <BrandMark />
        Library
      </NavbarBrand>
      <NavbarToggler onClick={() => setIsOpen((open) => !open)} aria-label="Toggle navigation" />
      <Collapse isOpen={isOpen} navbar>
        <Nav className="me-auto" navbar>
          {(SECTIONS[user?.role] ?? [])
            // Demo logins cannot administer accounts (the API refuses), so don't offer it.
            .filter(({ to }) => !(user?.demoAccount && to === '/users'))
            .map(({ to, label }) => (
            <NavItem key={to}>
              <NavLink tag={RouterNavLink} to={to} onClick={close}>
                {label}
              </NavLink>
            </NavItem>
          ))}
        </Nav>
        {user && (
          <Nav navbar className="align-items-md-center gap-md-2">
            <NavItem>
              <NavLink
                tag={RouterNavLink}
                to="/notifications"
                onClick={close}
                aria-label={unread > 0 ? `Notifications (${unread} unread)` : 'Notifications'}
              >
                <span aria-hidden="true">🔔</span>
                {unread > 0 && (
                  <Badge color="danger" pill className="ms-1">
                    {unread}
                  </Badge>
                )}
              </NavLink>
            </NavItem>
            <NavItem>
              <NavLink tag={RouterNavLink} to="/profile" onClick={close}>
                {user.displayName}{' '}
                <Badge color={isLibrarian ? 'warning' : 'light'} pill className="text-dark">
                  {isLibrarian ? 'Librarian' : 'Member'}
                </Badge>
              </NavLink>
            </NavItem>
            <NavItem>
              <Button size="sm" outline color="light" onClick={logout}>
                Log out
              </Button>
            </NavItem>
          </Nav>
        )}
      </Collapse>
    </Navbar>
  );
}
