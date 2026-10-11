import { cn } from "@/lib/utils";
import type { EventStatus, RegistrationStatus } from "@/lib/api/types";

const BASE =
  "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset";

const EVENT_STATUS: Record<EventStatus, { label: string; className: string }> = {
  DRAFT: { label: "草稿", className: "bg-slate-50 text-slate-600 ring-slate-200" },
  PUBLISHED: {
    label: "报名中",
    className: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  },
  CANCELLED: { label: "已取消", className: "bg-red-50 text-red-700 ring-red-200" },
  FINISHED: { label: "已结束", className: "bg-slate-100 text-slate-500 ring-slate-200" },
};

const REGISTRATION_STATUS: Record<
  RegistrationStatus,
  { label: string; className: string }
> = {
  PENDING: { label: "待处理", className: "bg-amber-50 text-amber-700 ring-amber-200" },
  CONFIRMED: {
    label: "已确认",
    className: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  },
  WAITLISTED: { label: "候补中", className: "bg-amber-50 text-amber-700 ring-amber-200" },
  CANCELLED: { label: "已取消", className: "bg-slate-100 text-slate-500 ring-slate-200" },
  CHECKED_IN: { label: "已签到", className: "bg-sky-50 text-sky-700 ring-sky-200" },
};

export function EventStatusBadge({ status }: { status: EventStatus }) {
  const config = EVENT_STATUS[status];
  return <span className={cn(BASE, config.className)}>{config.label}</span>;
}

export function RegistrationStatusBadge({
  status,
}: {
  status: RegistrationStatus;
}) {
  const config = REGISTRATION_STATUS[status];
  return <span className={cn(BASE, config.className)}>{config.label}</span>;
}

export function Badge({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <span className={cn(BASE, "bg-slate-50 text-slate-600 ring-slate-200", className)}>
      {children}
    </span>
  );
}
