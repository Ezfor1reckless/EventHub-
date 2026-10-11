"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, setAccessToken, setOnAuthLost } from "@/lib/api/client";
import type { UserResponse } from "@/lib/api/types";

type AuthStatus = "loading" | "authenticated" | "anonymous";

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
  /** 会话失效（刷新令牌也过期）时被调用，供上层清理缓存。 */
  onSessionLost: (callback: () => void) => () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setStatus("anonymous");
  }, []);

  // 应用启动时尝试恢复会话：访问令牌只在内存里，刷新页面后就没了，
  // 但 HttpOnly Cookie 里的刷新令牌还在，用它换一个新的访问令牌。
  useEffect(() => {
    let cancelled = false;

    (async () => {
      const refreshed = await api.auth.refresh();
      if (cancelled) return;

      if (!refreshed) {
        setStatus("anonymous");
        return;
      }
      try {
        const me = await api.me.get();
        if (cancelled) return;
        setUser(me);
        setStatus("authenticated");
      } catch {
        if (!cancelled) clearSession();
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [clearSession]);

  // 令牌彻底失效时，把状态切回未登录
  useEffect(() => {
    setOnAuthLost(clearSession);
    return () => setOnAuthLost(null);
  }, [clearSession]);

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await api.auth.login({ email, password });
    setAccessToken(tokens.accessToken);
    const me = await api.me.get();
    setUser(me);
    setStatus("authenticated");
  }, []);

  const register = useCallback(
    async (input: {
      email: string;
      password: string;
      nickname: string;
      phone?: string;
    }) => {
      const tokens = await api.auth.register(input);
      setAccessToken(tokens.accessToken);
      const me = await api.me.get();
      setUser(me);
      setStatus("authenticated");
    },
    [],
  );

  const logout = useCallback(async () => {
    try {
      await api.auth.logout();
    } catch {
      // 服务端登出失败也要清掉本地状态——否则用户点了登出却还留在登录态，
      // 那比「服务端会话没清干净」更让人困惑
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      status,
      login,
      register,
      logout,
      onSessionLost: (callback: () => void) => {
        setOnAuthLost(callback);
        return () => setOnAuthLost(null);
      },
    }),
    [user, status, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth 必须在 <AuthProvider> 内使用");
  }
  return context;
}

export function useIsOrganizer(): boolean {
  const { user } = useAuth();
  return Boolean(user?.roles?.includes("ORGANIZER") || user?.roles?.includes("ADMIN"));
}
