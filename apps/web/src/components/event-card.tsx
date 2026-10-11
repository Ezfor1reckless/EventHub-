import Link from "next/link";
import { EventStatusBadge } from "@/components/ui/badge";
import { formatDateTime } from "@/lib/utils";
import type { EventResponse } from "@/lib/api/types";

export function EventCard({ event }: { event: EventResponse }) {
  const full = event.seatsRemaining <= 0;

  return (
    <Link
      href={`/events/${event.id}`}
      className="block rounded-lg border border-slate-200 bg-white p-5 shadow-sm transition hover:border-slate-300 hover:shadow"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="line-clamp-2 font-medium text-slate-900">{event.title}</h3>
        <EventStatusBadge status={event.status} />
      </div>

      <dl className="mt-3 space-y-1 text-sm text-slate-600">
        <div className="flex gap-2">
          <dt className="shrink-0 text-slate-400">时间</dt>
          <dd>{formatDateTime(event.startAt)}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="shrink-0 text-slate-400">地点</dt>
          <dd className="truncate">{event.location}</dd>
        </div>
      </dl>

      <div className="mt-4 flex items-center gap-3 text-sm">
        <span className={full ? "text-amber-600" : "text-emerald-600"}>
          {full ? "已满，可候补" : `剩余 ${event.seatsRemaining} / ${event.capacity}`}
        </span>
        {event.waitlistedCount > 0 ? (
          <span className="text-slate-400">候补 {event.waitlistedCount} 人</span>
        ) : null}
      </div>
    </Link>
  );
}
