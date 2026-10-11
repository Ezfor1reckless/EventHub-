"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { RequireAuth } from "@/components/require-auth";
import { Button } from "@/components/ui/button";
import { EventStatusBadge } from "@/components/ui/badge";
import { EmptyState, ErrorAlert, LoadingBlock } from "@/components/ui/feedback";
import { formatDateTime } from "@/lib/utils";
import type { EventResponse } from "@/lib/api/types";

export default function OrganizerEventsPage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <RequireAuth>
        <OrganizerEvents />
      </RequireAuth>
    </Suspense>
  );
}

function OrganizerEvents() {
  const queryClient = useQueryClient();
  const [actionError, setActionError] = useState<unknown>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ["events", "mine"],
    queryFn: () => api.me.events({ size: 50 }),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["events"] });
  };

  const publishMutation = useMutation({
    mutationFn: (id: number) => api.events.publish(id),
    onSuccess: invalidate,
    onError: setActionError,
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => api.events.cancel(id),
    onSuccess: invalidate,
    onError: setActionError,
  });

  if (isLoading) return <LoadingBlock />;
  if (error) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-10">
        <ErrorAlert error={error} />
      </div>
    );
  }

  const items = data?.items ?? [];
  const busy = publishMutation.isPending || cancelMutation.isPending;

  return (
    <div className="mx-auto max-w-4xl px-4 py-10">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">我组织的活动</h1>
        <Link href="/organizer/events/new">
          <Button>创建活动</Button>
        </Link>
      </div>

      {actionError ? (
        <div className="mb-4">
          <ErrorAlert error={actionError} />
        </div>
      ) : null}

      {items.length === 0 ? (
        <EmptyState
          title="你还没有创建过活动"
          description="创建后需要发布，其他用户才能看到并报名。"
          action={
            <Link href="/organizer/events/new">
              <Button>创建第一个活动</Button>
            </Link>
          }
        />
      ) : (
        <ul className="space-y-3">
          {items.map((event) => (
            <OrganizerEventItem
              key={event.id}
              event={event}
              busy={busy}
              onPublish={() => publishMutation.mutate(event.id)}
              onCancel={() => cancelMutation.mutate(event.id)}
            />
          ))}
        </ul>
      )}
    </div>
  );
}

function OrganizerEventItem({
  event,
  busy,
  onPublish,
  onCancel,
}: {
  event: EventResponse;
  busy: boolean;
  onPublish: () => void;
  onCancel: () => void;
}) {
  // DRAFT 和 PUBLISHED 才可编辑；已取消/已结束的活动改了对谁都没意义
  const editable = event.status === "DRAFT" || event.status === "PUBLISHED";
  const cancellable = event.status === "DRAFT" || event.status === "PUBLISHED";

  return (
    <li className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <Link
            href={`/organizer/events/${event.id}`}
            className="font-medium text-slate-900 hover:underline"
          >
            {event.title}
          </Link>
          <p className="mt-1 text-sm text-slate-500">
            {formatDateTime(event.startAt)} · {event.location}
          </p>
          <p className="mt-1 text-sm text-slate-600">
            已确认 {event.confirmedCount} / {event.capacity}
            {event.waitlistedCount > 0
              ? ` · 候补 ${event.waitlistedCount}`
              : ""}
          </p>
        </div>
        <EventStatusBadge status={event.status} />
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        {event.status === "DRAFT" ? (
          <Button size="sm" disabled={busy} onClick={onPublish}>
            发布
          </Button>
        ) : null}

        <Link href={`/organizer/events/${event.id}`}>
          <Button variant="secondary" size="sm">
            {editable ? "管理" : "查看"}
          </Button>
        </Link>

        {cancellable ? (
          <Button
            variant="ghost"
            size="sm"
            disabled={busy}
            className="text-red-600 hover:bg-red-50"
            onClick={() => {
              // 取消活动会清空候补队列并给已确认的用户发通知，是不可逆的
              if (
                window.confirm(
                  `确定取消「${event.title}」吗？\n候补队列会被清空，已确认的用户会收到通知。此操作不可撤销。`,
                )
              ) {
                onCancel();
              }
            }}
          >
            取消活动
          </Button>
        ) : null}
      </div>
    </li>
  );
}
