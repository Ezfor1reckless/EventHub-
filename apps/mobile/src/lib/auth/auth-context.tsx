import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import { api, setOnAuthLost } from '@/lib/api/client';
import type { UserResponse } from '@/lib/api/types';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  user: UserResponse | null;
  status: AuthStatus;
  login: (email: string, password: string) => Promise<void>;
  register: (input: {
    email: string;
    password: string;
    nickname: string;
    phone?: string;
  }) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');

  const clearSession = useCallback(() => {
    setUser(null);
    setStatus('anonymous');
  }, []);

  // 启动时恢复会话：访问令牌只在内存里，App 重启后就没有了，
  // 但 SecureStore 里的刷新令牌还在，用它换一个新的。
  useEffect(() => {
    let cancelled = false;

    (async () => {
      const restored = await api.auth.restore();
      if (cancelled) return;

      if (!restored) {
        setStatus('anonymous');
        return;
      }
      try {
        const me = await api.me.get();
        if (cancelled) return;
        setUser(me);
        setStatus('authenticated');
      } catch {
        if (!cancelled) clearSession();
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [clearSession]);

  useEffect(() => {
    setOnAuthLost(clearSession);
    return () => setOnAuthLost(null);
  }, [clearSession]);

  const login = useCallback(async (email: string, password: string) => {
    await api.auth.login({ email, password });
    setUser(await api.me.get());
    setStatus('authenticated');
  }, []);

  const register = useCallback(
    async (input: {
      email: string;
      password: string;
      nickname: string;
      phone?: string;
    }) => {
      await api.auth.register(input);
      setUser(await api.me.get());
      setStatus('authenticated');
    },
    [],
  );

  const logout = useCallback(async () => {
    await api.auth.logout();
    clearSession();
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, status, login, register, logout }),
    [user, status, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 <AuthProvider> 内使用');
  }
  return context;
}

export function useIsOrganizer(): boolean {
  const { user } = useAuth();
  return Boolean(
    user?.roles?.includes('ORGANIZER') || user?.roles?.includes('ADMIN'),
  );
}
