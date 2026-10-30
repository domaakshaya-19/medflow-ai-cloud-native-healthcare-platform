import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { SESSION_EXPIRED_EVENT, TOKEN_KEY } from '../api';
import { authApi } from '../api/auth';

const AuthContext = createContext(null);
export const useAuth = () => useContext(AuthContext);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionMessage, setSessionMessage] = useState('');

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setUser(null);
  }, []);

  useEffect(() => {
    const expire = () => {
      logout();
      setSessionMessage('Your session expired. Please sign in again.');
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, expire);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
  }, [logout]);

  useEffect(() => {
    if (!localStorage.getItem(TOKEN_KEY)) {
      setLoading(false);
      return;
    }
    authApi.me().then(setUser).catch(logout).finally(() => setLoading(false));
  }, [logout]);

  async function login(credentials) {
    const response = await authApi.login(credentials);
    localStorage.setItem(TOKEN_KEY, response.accessToken);
    setSessionMessage('');
    setUser(response.user);
    return response.user;
  }

  const value = useMemo(() => ({ user, loading, login, logout, sessionMessage, setSessionMessage }),
    [user, loading, logout, sessionMessage]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
