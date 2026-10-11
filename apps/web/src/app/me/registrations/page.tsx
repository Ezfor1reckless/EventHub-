"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { RequireAuth } from "@/components/require-auth";
import { Button } from "@/components/ui/button";
import { RegistrationStatusBadge } from "@/components/ui/badge";
import { EmptyState, ErrorAlert, LoadingBlock } from "@/components/ui/feedback";
import { TicketQr } from "@/components/ticket-qr";
import { formatDateTime } from "@/lib/utils";
import type { RegistrationResponse } from "@/lib/api/types";

export default function MyRegistrationsPage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <RequireAuth>
        <MyRegistrations />
      </RequireAuth>
    </Suspense>
  );
}

function MyRegistrations() {
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState<number | null>(null);
  const [page, setPage] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ["registrations", "mine", page],
    queryFn: () => api.me.registrations({ page, size: 10 }),
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => api.registrations.cancel(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["registrations", "mine"] });
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
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">我的报名</h1>

      {cancelMutation.error ? (
        <div className="mb-4">
          <ErrorAlert error={cancelMutation.error} />
        </div>
      ) : null}

      {items.length === 0 ? (
        <EmptyState
          title="还没有报名任何活动"
          description="去看看有哪些活动正在报名吧。"
          action={
            <Link href="/events">
              <Button>浏览活动</Button>
            </Link>
          }
        />
      ) : (
        <ul className="space-y-3">
          {items.map((registration) => (
            <RegistrationItem
              key={registration.id}
              registration={registration}
              expanded={expanded === registration.id}
              onToggle={() =>
                setExpanded(expanded === registration.id ? null : registration.id)
              }
              onCancel={() => cancelMutation.mutate(registration.id)}
              cancelling={cancelMutation.isPending}
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

function RegistrationItem({
  registration,
  expanded,
  onToggle,
  onCancel,
  cancelling,
}: {
  registration: RegistrationResponse;
  expanded: boolean;
  onToggle: () => void;
  onCancel: () => void;
  cancelling: boolean;
}) {
  const { event, status } = registration;
  const canCancel = status !== "CANCELLED" && status !== "CHECKED_IN";

  return (
    <li className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <Link
            href={`/events/${event.id}`}
            className="font-medium text-slate-900 hover:underline"
          >
            {event.title}
          </Link>
          <p className="mt-1 text-sm text-slate-500">
            {formatDateTime(event.startAt)} · {event.location}
          </p>
        </div>
        <RegistrationStatusBadge status={status} />
      </div>

      {status === "WAITLISTED" ? (
        <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">
          你在候补队列中。一旦有人取消，系统会按候补顺序自动把名额递补给你，
          并给你发一条通知。
        </p>
      ) : null}

      <div className="mt-3 flex items-center gap-2">
        {status === "CONFIRMED" || status === "CHECKED_IN" ? (
          <Button variant="secondary" size="sm" onClick={onToggle}>
            {expanded ? "收起票券" : "查看票券"}
          </Button>
        ) : null}
        {canCancel ? (
          <Button
            variant="ghost"
            size="sm"
            disabled={cancelling}
            onClick={onCancel}
            className="text-red-600 hover:bg-red-50"
          >
            {status === "WAITLISTED" ? "取消候补" : "取消报名"}
          </Button>
        ) : null}
      </div>

      {expanded ? (
        <div className="mt-4 flex flex-col items-center gap-3 border-t border-slate-100 pt-4 sm:flex-row sm:items-start">
          <TicketQr value={registration.ticketCode} />
          <div className="text-center sm:text-left">
            <p className="text-xs text-slate-500">票券码</p>
            <p className="font-mono text-base tracking-widest text-slate-900">
              {registration.ticketCode}
            </p>
            <p className="mt-3 max-w-xs text-xs text-slate-500">
              活动当天向组织者出示这个二维码，或用它完成自助签到。
            </p>
          </div>
        </div>
      ) : null}
    </li>
  );
}
