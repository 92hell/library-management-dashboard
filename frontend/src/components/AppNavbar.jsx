import { useState } from 'react';
import { Link, NavLink as RouterNavLink } from 'react-router-dom';
import { Badge, Button, Collapse, Nav, Navbar, NavbarBrand, NavbarToggler, NavItem, NavLink } from 'reactstrap';
import { useAuth } from '../auth';
import { useNotifications } from '../notifications';
import { msg, useT } from '../i18n';
import BrandMark from './BrandMark';
import LanguageSwitch from './LanguageSwitch';

const SECTIONS = {
  LIBRARIAN: [
    { to: '/borrowed-books', label: msg('Loans') },
    { to: '/books', label: msg('Books') },
    { to: '/authors', label: msg('Authors') },
    { to: '/members', label: msg('Members') },
    { to: '/fines', label: msg('Fines') },
    { to: '/reports', label: msg('Reports') },
    { to: '/users', label: msg('Users') },
  ],
  CUSTOMER: [
    { to: '/catalogue', label: msg('Catalogue') },
    { to: '/my/loans', label: msg('My loans') },
  ],
};

export default function AppNavbar() {
  const { user, isLibrarian, logout } = useAuth();
  const { unread } = useNotifications();
  const [isOpen, setIsOpen] = useState(false);
  const close = () => setIsOpen(false);
  const t = useT();

  return (
    <Navbar dark expand="md" className="px-3 library-nav">
      <NavbarBrand tag={Link} to="/">
        <BrandMark />
        {t('Library')}
      </NavbarBrand>
      <NavbarToggler onClick={() => setIsOpen((open) => !open)} aria-label={t('Toggle navigation')} />
      <Collapse isOpen={isOpen} navbar>
        <Nav className="me-auto" navbar>
          {(SECTIONS[user?.role] ?? [])
            // Demo logins cannot administer accounts (the API refuses), so don't offer it.
            .filter(({ to }) => !(user?.demoAccount && to === '/users'))
            .map(({ to, label }) => (
            <NavItem key={to}>
              <NavLink tag={RouterNavLink} to={to} onClick={close}>
                {t(label)}
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
                aria-label={unread > 0 ? t('Notifications ({count} unread)', { count: unread }) : t('Notifications')}
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
                  {isLibrarian ? t('Librarian') : t('Member')}
                </Badge>
              </NavLink>
            </NavItem>
            <NavItem>
              <Button size="sm" outline color="light" onClick={logout}>
                {t('Log out')}
              </Button>
            </NavItem>
            <NavItem>
              <LanguageSwitch dark className="my-2 my-md-0" />
            </NavItem>
          </Nav>
        )}
      </Collapse>
    </Navbar>
  );
}
