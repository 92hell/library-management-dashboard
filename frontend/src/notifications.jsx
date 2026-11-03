import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api } from './api';

const POLL_INTERVAL_MS = 60_000;
const NotificationsContext = createContext({ unread: 0, refresh: () => {} });

/** Keeps the unread notification count fresh: on mount, every minute, and when asked to refresh. */
export function NotificationsProvider({ children }) {
  const [unread, setUnread] = useState(0);

  const refresh = useCallback(() => {
    api.get('/api/notifications/unread-count').then(
      (result) => setUnread(result?.count ?? 0),
      () => {}, // A failed poll is not worth interrupting the user for; the next one will retry.
    );
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  const value = useMemo(() => ({ unread, refresh }), [unread, refresh]);
  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>;
}

export function useNotifications() {
  return useContext(NotificationsContext);
}
