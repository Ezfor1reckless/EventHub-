"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { RequireAuth } from "@/components/require-auth";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorAlert, LoadingBlock } from "@/components/ui/feedback";
import { cn, formatDateTime } from "@/lib/utils";
import type { NotificationResponse } from "@/lib/api/types";

export default function NotificationsPage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <RequireAuth>
        <Notifications />
      </RequireAuth>
    </Suspense>
  );
}

function Notifications() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ["notifications", "list", page],
    queryFn: () => api.me.notifications({ page, size: 20 }),
  });

  const markRead = useMutation({
    mutationFn: (id: number) => api.me.markNotificationRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  if (isLoading) return <LoadingBlock />;
  if (error) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <ErrorAlert error={error} />
      </div>
    );
  }

  const items = data?.items ?? [];

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">通知</h1>

      {items.length === 0 ? (
        <EmptyState
          title="还没有收到通知"
          description="报名成功、候补转正、活动取消时都会在这里提醒你。"
        />
      ) : (
        <ul className="space-y-2">
          {items.map((notification) => (
            <NotificationItem
              key={notification.id}
              notification={notification}
              onMarkRead={() => markRead.mutate(notification.id)}
              marking={markRead.isPending}
            />
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 ? (
        <nav className="mt-6 flex items-center justify-between text-sm">
          <Button
            variant="secondary"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            上一页
          </Button>
          <span className="text-slate-400">
            第 {data.page + 1} / {data.totalPages} 页
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={!data.hasNext}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
          </Button>
        </nav>
      ) : null}
    </div>
  );
}

function NotificationItem({
  notification,
  onMarkRead,
  marking,
}: {
  notification: NotificationResponse;
  onMarkRead: () => void;
  marking: boolean;
}) {
  const unread = notification.readAt === null;
  const eventHref =
    notification.relatedType === "Event" && notification.relatedId
      ? `/events/${notification.relatedId}`
      : null;

  return (
    <li
      className={cn(
        "rounded-lg border bg-white p-4 shadow-sm",
        unread ? "border-slate-300" : "border-slate-200",
      )}
    >
      <div className="flex items-start gap-3">
        {unread ? (
          // 未读点。只用颜色区分「已读/未读」对色觉障碍用户不友好，
          // 所以同时给一个形状标记，并在文字里说明。
          <span
            className="mt-1.5 size-2 shrink-0 rounded-full bg-red-500"
            aria-label="未读"
          />
        ) : (
          <span className="mt-1.5 size-2 shrink-0 rounded-full bg-transparent" />
        )}

        <div className="min-w-0 flex-1">
          <div className="flex items-baseline justify-between gap-3">
            <p
              className={cn(
                "text-sm",
                unread ? "font-medium text-slate-900" : "text-slate-700",
              )}
            >
              {notification.title}
            </p>
            <span className="shrink-0 text-xs text-slate-400">
              {formatDateTime(notification.createdAt)}
            </span>
          </div>

          <p className="mt-1 text-sm text-slate-600">{notification.content}</p>

          <div className="mt-2 flex items-center gap-3">
            {eventHref ? (
              <Link
                href={eventHref}
                className="text-xs text-slate-900 underline"
              >
                查看活动
              </Link>
            ) : null}
            {unread ? (
              <button
                onClick={onMarkRead}
                disabled={marking}
                className="text-xs text-slate-500 hover:text-slate-900 disabled:opacity-50"
              >
                标记已读
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </li>
  );
}
