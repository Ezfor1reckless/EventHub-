import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import type {
  ApiEnvelope,
  CheckInResponse,
  CheckInSessionResponse,
  CheckInStatsResponse,
  EventRequest,
  EventResponse,
  NotificationResponse,
  PageResponse,
  RegistrationResponse,
  TokenResponse,
  UserResponse,
} from './types';

const API_BASE =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

const REFRESH_TOKEN_KEY = 'eventhub_refresh_token';

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly fieldErrors: Record<string, string> | undefined;

  constructor(
    code: string,
    message: string,
    status: number,
    fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

// ---------------------------------------------------------------------------
// 令牌存储
//
// 刷新令牌放 SecureStore（iOS Keychain / Android Keystore），不是 AsyncStorage：
// AsyncStorage 在已 root 的设备上是明文可读的，而刷新令牌有效期 7 天，
// 拿到它就等于长期接管账号。
//
// Web 预览降级到 localStorage —— 浏览器里没有 SecureStore，且 Web 版本来就
// 走 HttpOnly Cookie，这里只是让 `expo start --web` 能跑起来。
// ---------------------------------------------------------------------------

async function readRefreshToken(): Promise<string | null> {
  try {
    if (Platform.OS === 'web') {
      return globalThis.localStorage?.getItem(REFRESH_TOKEN_KEY) ?? null;
    }
    return await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
  } catch {
    return null;
  }
}

async function writeRefreshToken(token: string | null): Promise<void> {
  try {
    if (Platform.OS === 'web') {
      if (token) globalThis.localStorage?.setItem(REFRESH_TOKEN_KEY, token);
      else globalThis.localStorage?.removeItem(REFRESH_TOKEN_KEY);
      return;
    }
    if (token) {
      await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token);
    } else {
      await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
    }
  } catch {
    // 存储失败不该让整个登录流程崩掉；用户下次启动重新登录即可
  }
}

/** 访问令牌只存在内存，进程结束时消失。 */
let accessToken: string | null = null;
let onAuthLost: (() => void) | null = null;

export function setOnAuthLost(callback: (() => void) | null) {
  onAuthLost = callback;
}

// ---------------------------------------------------------------------------
// 刷新（单飞）
// ---------------------------------------------------------------------------

let refreshInFlight: Promise<boolean> | null = null;

export function refreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function performRefresh(): Promise<boolean> {
  const refreshToken = await readRefreshToken();
  if (!refreshToken) {
    return false;
  }

  try {
    const response = await fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      // 移动端没有 Cookie，刷新令牌走请求体
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) {
      await writeRefreshToken(null);
      return false;
    }

    const envelope = (await response.json()) as ApiEnvelope<TokenResponse>;
    if (!envelope.success || !envelope.data?.accessToken) {
      await writeRefreshToken(null);
      return false;
    }

    accessToken = envelope.data.accessToken;
    // 刷新令牌是轮换式的：旧的已经作废，必须把新的存下来。
    // 漏掉这一步的表现是「用一段时间后突然被登出」，且只在重启 App 后复现。
    await writeRefreshToken(envelope.data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// 请求
// ---------------------------------------------------------------------------

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, headers, ...init } = options;

  const send = () =>
    fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

  let response = await send();

  if (response.status === 401) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      response = await send();
    } else {
      accessToken = null;
      onAuthLost?.();
    }
  }

  const raw = await response.text();
  let envelope: ApiEnvelope<T> | null = null;
  if (raw) {
    try {
      envelope = JSON.parse(raw) as ApiEnvelope<T>;
    } catch {
      throw new ApiError(
        'INVALID_RESPONSE',
        `服务返回了非 JSON 响应（HTTP ${response.status}）`,
        response.status,
      );
    }
  }

  if (!response.ok || (envelope && !envelope.success)) {
    throw new ApiError(
      envelope?.code ?? 'UNKNOWN_ERROR',
      envelope?.message ?? `请求失败（HTTP ${response.status}）`,
      response.status,
      envelope?.data as Record<string, string> | undefined,
    );
  }

  return (envelope as ApiEnvelope<T>).data;
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value));
  }
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

// ---------------------------------------------------------------------------
// 资源接口
// ---------------------------------------------------------------------------

export const api = {
  auth: {
    async register(body: {
      email: string;
      password: string;
      nickname: string;
      phone?: string;
    }) {
      const tokens = await request<TokenResponse>('/auth/register', {
        method: 'POST',
        body,
      });
      accessToken = tokens.accessToken;
      await writeRefreshToken(tokens.refreshToken);
      return tokens;
    },

    async login(body: { email: string; password: string }) {
      const tokens = await request<TokenResponse>('/auth/login', {
        method: 'POST',
        body,
      });
      accessToken = tokens.accessToken;
      await writeRefreshToken(tokens.refreshToken);
      return tokens;
    },

    /** 应用启动时恢复会话：用 SecureStore 里的刷新令牌换一个新的访问令牌。 */
    restore: () => refreshAccessToken(),

    async logout() {
      try {
        await request<void>('/auth/logout', { method: 'POST' });
      } finally {
        // 服务端登出失败也要清本地，否则用户点了登出还留在登录态
        accessToken = null;
        await writeRefreshToken(null);
      }
    },
  },

  me: {
    get: () => request<UserResponse>('/me'),
    events: (params: { page?: number; size?: number } = {}) =>
      request<PageResponse<EventResponse>>(`/me/events${query(params)}`),
    registrations: (params: { page?: number; size?: number } = {}) =>
      request<PageResponse<RegistrationResponse>>(
        `/me/registrations${query(params)}`,
      ),
    notifications: (params: { page?: number; size?: number } = {}) =>
      request<PageResponse<NotificationResponse>>(
        `/me/notifications${query(params)}`,
      ),
    unreadNotificationCount: () =>
      request<number>('/me/notifications/unread-count'),
    markNotificationRead: (id: number) =>
      request<void>(`/me/notifications/${id}/read`, { method: 'POST' }),
  },

  events: {
    list: (params: { page?: number; size?: number; sort?: string } = {}) =>
      request<PageResponse<EventResponse>>(`/events${query(params)}`),
    get: (id: number) => request<EventResponse>(`/events/${id}`),
    create: (body: EventRequest) =>
      request<EventResponse>('/events', { method: 'POST', body }),
    update: (id: number, body: EventRequest) =>
      request<EventResponse>(`/events/${id}`, { method: 'PUT', body }),
    publish: (id: number) =>
      request<EventResponse>(`/events/${id}/publish`, { method: 'POST' }),
    cancel: (id: number) =>
      request<EventResponse>(`/events/${id}/cancel`, { method: 'POST' }),
  },

  registrations: {
    create: (eventId: number) =>
      request<RegistrationResponse>(`/events/${eventId}/registrations`, {
        method: 'POST',
      }),
    cancel: (registrationId: number) =>
      request<void>(`/registrations/${registrationId}`, { method: 'DELETE' }),
    findMine: (eventId: number) =>
      request<RegistrationResponse | null>(
        `/events/${eventId}/registrations/me`,
      ),
    listByEvent: (
      eventId: number,
      params: { page?: number; size?: number } = {},
    ) =>
      request<PageResponse<RegistrationResponse>>(
        `/events/${eventId}/registrations${query(params)}`,
      ),
  },

  checkin: {
    openSession: (eventId: number) =>
      request<CheckInSessionResponse>(`/events/${eventId}/checkin-session`, {
        method: 'POST',
      }),
    scan: (token: string) =>
      request<CheckInResponse>('/checkins/scan', {
        method: 'POST',
        body: { token },
      }),
    manual: (eventId: number, ticketCode: string) =>
      request<CheckInResponse>(`/events/${eventId}/checkins/manual`, {
        method: 'POST',
        body: { ticketCode },
      }),
    stats: (eventId: number) =>
      request<CheckInStatsResponse>(`/events/${eventId}/checkins`),
  },
};
