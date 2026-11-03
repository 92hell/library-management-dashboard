import { useEffect, useRef } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useT } from '../i18n';
import { NotificationsProvider } from '../notifications';
import AppNavbar from './AppNavbar';
import DemoBanner from './DemoBanner';

/**
 * After navigating to another page, move keyboard focus to the page content (as a full page load
 * would), so screen-reader and keyboard users start at the new page rather than at the old link.
 */
function useFocusMainOnNavigation(mainRef) {
  const { pathname } = useLocation();
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    mainRef.current?.focus({ preventScroll: false });
  }, [pathname, mainRef]);
}

export default function Layout() {
  const t = useT();
  const mainRef = useRef(null);
  useFocusMainOnNavigation(mainRef);
  return (
    <NotificationsProvider>
      <a href="#main" className="visually-hidden-focusable skip-link">
        {t('Skip to main content')}
      </a>
      <AppNavbar />
      <DemoBanner />
      <main id="main" tabIndex={-1} className="container py-4" ref={mainRef}>
        <Outlet />
      </main>
    </NotificationsProvider>
  );
}
