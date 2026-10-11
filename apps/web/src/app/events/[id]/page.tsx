import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { serverGet } from "@/lib/api/server";
import { EventStatusBadge } from "@/components/ui/badge";
import { EventActions } from "@/components/event-actions";
import { EmptyState, LoadingBlock } from "@/components/ui/feedback";
import { formatDateTime } from "@/lib/utils";
import type { EventResponse } from "@/lib/api/types";

export default function EventDetailPage({ params }: PageProps<"/events/[id]">) {
  return (
    <div className="mx-auto max-w-4xl px-4 py-10">
      <Suspense fallback={<LoadingBlock label="正在加载活动…" />}>
        <EventDetail params={params} />
      </Suspense>
    </div>
  );
}

async function EventDetail({ params }: { params: PageProps<"/events/[id]">["params"] }) {
  const { id } = await params;
  const eventId = Number(id);
  if (!Number.isInteger(eventId) || eventId <= 0) {
    notFound();
  }

  const event = await serverGet<EventResponse>(`/events/${eventId}`);

  // 后端对「不存在」和「草稿但你不是组织者」都返回 404，这里一并处理。
  // 二者不区分是刻意的：区分了就泄露了「这个 id 存在」这一事实。
  if (event === null) {
    return (
      <EmptyState
        title="活动不存在或不可见"
        description="它可能已被删除，或者还是草稿状态。"
        action={
          <Link href="/events" className="text-sm text-slate-900 underline">
            返回活动列表
          </Link>
        }
      />
    );
  }

  const full = event.seatsRemaining <= 0;

  return (
    <div className="grid gap-8 lg:grid-cols-3">
      <article className="lg:col-span-2">
        <div className="flex items-start gap-3">
          <h1 className="text-2xl font-semibold text-slate-900">{event.title}</h1>
          <EventStatusBadge status={event.status} />
        </div>

        <dl className="mt-6 space-y-3 border-y border-slate-200 py-5 text-sm">
          <div className="flex gap-4">
            <dt className="w-16 shrink-0 text-slate-400">时间</dt>
            <dd className="text-slate-700">
              {formatDateTime(event.startAt)} — {formatDateTime(event.endAt)}
            </dd>
          </div>
          <div className="flex gap-4">
            <dt className="w-16 shrink-0 text-slate-400">地点</dt>
            <dd className="text-slate-700">{event.location}</dd>
          </div>
          <div className="flex gap-4">
            <dt className="w-16 shrink-0 text-slate-400">名额</dt>
            <dd className="text-slate-700">
              {event.confirmedCount} / {event.capacity} 已确认
              {event.waitlistedCount > 0
                ? `，${event.waitlistedCount} 人候补中`
                : ""}
            </dd>
          </div>
        </dl>

        {event.description ? (
          <div className="mt-6 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
            {event.description}
          </div>
        ) : (
          <p className="mt-6 text-sm text-slate-400">组织者未填写活动详情。</p>
        )}
      </article>

      <aside className="lg:col-span-1">
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-sm text-slate-500">报名状态</p>
          <p
            className={`mt-1 text-lg font-medium ${full ? "text-amber-600" : "text-emerald-600"}`}
          >
            {full ? "名额已满" : `剩余 ${event.seatsRemaining} 个名额`}
          </p>
          <div className="mt-4">
            <EventActions eventId={event.id} eventStatus={event.status} />
          </div>
        </div>
      </aside>
    </div>
  );
}
