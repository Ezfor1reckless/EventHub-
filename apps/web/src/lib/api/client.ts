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
} from "./types";

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

/** 后端返回的业务错误。 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  /** 参数校验失败时的字段级错误，用于表单标红。 */
  readonly fieldErrors: Record<string, string> | undefined;

  constructor(
    code: string,
    message: string,
    status: number,
    fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

// ---------------------------------------------------------------------------
// 访问令牌只存在内存里
//
// 刻意不写 localStorage：那里存的东西任何一段注入的 JavaScript 都能读走，
// 一次 XSS 就等于令牌泄露。内存变量在页面刷新后消失，而「刷新页面后仍然登录」
// 由 HttpOnly Cookie 里的刷新令牌完成——那段令牌 JS 根本看不见。
// ---------------------------------------------------------------------------

let accessToken: string | null = null;
let onAuthLost: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

/** 注册「刷新也救不回来」时的回调，由 AuthProvider 用来把状态切回未登录。 */
export function setOnAuthLost(callback: (() => void) | null) {
  onAuthLost = callback;
}

/**
 * 刷新访问令牌，单飞（single-flight）。
 *
 * 多个请求同时收到 401 时，若各自去刷新，会并发发出多个刷新请求；
 * 而刷新令牌是轮换式的——第一个请求成功后就作废了旧令牌，
 * 后面的请求拿着已经作废的令牌刷新会失败，还会被后端判定为「令牌重用」
 * 从而吊销该用户的全部会话。共享同一个 Promise 可以完全避免这个问题。
 */
let refreshInFlight: Promise<boolean> | null = null;

async function refreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function performRefresh(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      credentials: "include", // 带上 HttpOnly 的刷新令牌 Cookie
      headers: { "Content-Type": "application/json" },
    });
    if (!response.ok) {
      return false;
    }
    const envelope = (await response.json()) as ApiEnvelope<TokenResponse>;
    if (envelope.success && envelope.data?.accessToken) {
      accessToken = envelope.data.accessToken;
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// 请求
// ---------------------------------------------------------------------------

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, headers, ...init } = options;

  const send = () =>
    fetch(`${API_BASE}${path}`, {
      ...init,
      credentials: "include",
      headers: {
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

  let response = await send();

  // 访问令牌 15 分钟过期。过期后自动续期并重放一次，用户无感。
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
      // 后端正常情况下总是返回 JSON。走到这里说明响应被代理或网关替换了
      // （例如 Nginx 的 502 页面），此时给出比 "Unexpected token <" 更有用的信息。
      throw new ApiError(
        "INVALID_RESPONSE",
        `服务返回了非 JSON 响应（HTTP ${response.status}）`,
        response.status,
      );
    }
  }

  if (!response.ok || (envelope && !envelope.success)) {
    const code = envelope?.code ?? "UNKNOWN_ERROR";
    const message = envelope?.message ?? `请求失败（HTTP ${response.status}）`;
    const fieldErrors = envelope?.data as Record<string, string> | undefined;
    throw new ApiError(code, message, response.status, fieldErrors);
  }

  return (envelope as ApiEnvelope<T>).data;
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

// ---------------------------------------------------------------------------
// 各资源接口
// ---------------------------------------------------------------------------

export const api = {
  auth: {
    register: (body: {
      email: string;
      password: string;
      nickname: string;
      phone?: string;
    }) => request<TokenResponse>("/auth/register", { method: "POST", body }),

    login: (body: { email: string; password: string }) =>
      request<TokenResponse>("/auth/login", { method: "POST", body }),

    /** 手动刷新。正常情况下由 401 自动触发，此处供应用启动时恢复会话使用。 */
    refresh: () => refreshAccessToken(),

    logout: () => request<void>("/auth/logout", { method: "POST" }),
  },

  me: {
    get: () => request<UserResponse>("/me"),

    events: (params: { page?: number; size?: number } = {}) =>
      request<PageResponse<EventResponse>>(`/me/events${query(params)}`),

    registrations: (params: { page?: number; size?: number } = {}) =>
      request<PageResponse<RegistrationResponse>>(`/me/registrations${query(params)}`),

    notifications: (params: { page?: number; size?: number } = {}) =>
      request<PageResponse<NotificationResponse>>(
        `/me/notifications${query(params)}`,
      ),

    unreadNotificationCount: () =>
      request<number>("/me/notifications/unread-count"),

    markNotificationRead: (id: number) =>
      request<void>(`/me/notifications/${id}/read`, { method: "POST" }),
  },

  events: {
    list: (params: { page?: number; size?: number; sort?: string } = {}) =>
      request<PageResponse<EventResponse>>(`/events${query(params)}`),

    get: (id: number) => request<EventResponse>(`/events/${id}`),

    create: (body: EventRequest) =>
      request<EventResponse>("/events", { method: "POST", body }),

    update: (id: number, body: EventRequest) =>
      request<EventResponse>(`/events/${id}`, { method: "PUT", body }),

    publish: (id: number) =>
      request<EventResponse>(`/events/${id}/publish`, { method: "POST" }),

    cancel: (id: number) =>
      request<EventResponse>(`/events/${id}/cancel`, { method: "POST" }),
  },

  registrations: {
    create: (eventId: number) =>
      request<RegistrationResponse>(`/events/${eventId}/registrations`, {
        method: "POST",
      }),

    cancel: (registrationId: number) =>
      request<void>(`/registrations/${registrationId}`, { method: "DELETE" }),

    /** 我在某活动里的报名；没报名时后端返回 data: null。 */
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
        method: "POST",
      }),

    scan: (token: string) =>
      request<CheckInResponse>("/checkins/scan", {
        method: "POST",
        body: { token },
      }),

    manual: (eventId: number, ticketCode: string) =>
      request<CheckInResponse>(`/events/${eventId}/checkins/manual`, {
        method: "POST",
        body: { ticketCode },
      }),

    stats: (eventId: number) =>
      request<CheckInStatsResponse>(`/events/${eventId}/checkins`),
  },
};
