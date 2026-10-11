/**
 * 后端接口的响应类型。
 *
 * 这些类型与 docs/openapi.yaml 对应。理想情况下应当由 OpenAPI 规范生成，
 * 以免手写类型与后端漂移；当前后端 DTO 已稳定，先手写，
 * 后续可用 openapi-typescript 替换这一层而不影响调用方。
 */

export type EventStatus = "DRAFT" | "PUBLISHED" | "CANCELLED" | "FINISHED";

export type RegistrationStatus =
  | "PENDING"
  | "CONFIRMED"
  | "WAITLISTED"
  | "CANCELLED"
  | "CHECKED_IN";

export type Role = "USER" | "ORGANIZER" | "ADMIN";

export type CheckInMethod = "QR_SCAN" | "MANUAL";

export type NotificationType =
  | "REGISTRATION_CONFIRMED"
  | "WAITLIST_PROMOTED"
  | "EVENT_CANCELLED"
  | "EVENT_REMINDER";

/** 后端统一响应信封。 */
export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface UserResponse {
  id: number;
  email: string;
  nickname: string;
  avatarUrl: string | null;
  phone: string | null;
  roles: Role[];
  status: "ACTIVE" | "PENDING" | "DISABLED";
  createdAt: string;
}

export interface EventResponse {
  id: number;
  organizerId: number;
  title: string;
  description: string | null;
  coverUrl: string | null;
  location: string;
  startAt: string;
  endAt: string;
  capacity: number;
  status: EventStatus;
  confirmedCount: number;
  waitlistedCount: number;
  seatsRemaining: number;
  createdAt: string;
  updatedAt: string;
}

export interface EventSummary {
  id: number;
  title: string;
  location: string;
  coverUrl: string | null;
  startAt: string;
  endAt: string;
  status: EventStatus;
}

export interface RegistrationResponse {
  id: number;
  event: EventSummary;
  status: RegistrationStatus;
  ticketCode: string;
  waitlistedAt: string | null;
  registeredAt: string;
  cancelledAt: string | null;
}

export interface CheckInSessionResponse {
  token: string;
  expiresInSeconds: number;
}

export interface CheckInResponse {
  id: number;
  eventId: number;
  registrationId: number;
  ticketCode: string;
  checkedInAt: string;
  method: CheckInMethod;
}

export interface CheckInStatsResponse {
  eventId: number;
  confirmedCount: number;
  checkedInCount: number;
  waitlistedCount: number;
  checkInRate: number;
}

export interface NotificationResponse {
  id: number;
  type: NotificationType;
  title: string;
  content: string;
  status: "PENDING" | "SENT" | "FAILED";
  relatedType: string | null;
  relatedId: string | null;
  readAt: string | null;
  createdAt: string;
}

/** 创建/更新活动的表单负载。 */
export interface EventRequest {
  title: string;
  description?: string | null;
  coverUrl?: string | null;
  location: string;
  startAt: string;
  endAt: string;
  capacity: number;
}
