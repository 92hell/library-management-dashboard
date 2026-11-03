import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, onUnauthorized } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  // True after an explicit logout: the next sign-in starts on the home page instead of returning
  // to the page the previous user was on (a 401 / expired session still returns you there).
  const [loggedOut, setLoggedOut] = useState(false);

  useEffect(() => {
    let ignore = false;
    // Also primes the XSRF-TOKEN cookie, so it must run before any write request.
    api
      .get('/api/auth/me')
      .then(
        (me) => !ignore && setUser(me),
        () => !ignore && setUser(null),
      )
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, []);

  // Any 401 from the API means the session is gone; dropping the user sends RequireAuth to /login.
  useEffect(() => onUnauthorized(() => setUser(null)), []);

  const login = useCallback(async (username, password) => {
    const me = await api.post('/api/auth/login', { username, password });
    setLoggedOut(false);
    setUser(me);
    return me;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout');
    } finally {
      setLoggedOut(true);
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      loggedOut,
      isLibrarian: user?.role === 'LIBRARIAN',
      isCustomer: user?.role === 'CUSTOMER',
      login,
      logout,
    }),
    [user, loading, loggedOut, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
