import Link from "next/link";
import { Suspense } from "react";
import { serverGet } from "@/lib/api/server";
import { EventCard } from "@/components/event-card";
import { EmptyState, LoadingBlock } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import type { EventResponse, PageResponse } from "@/lib/api/types";

const PAGE_SIZE = 12;

export default function EventsPage({ searchParams }: PageProps<"/events">) {
  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">全部活动</h1>
      <Suspense fallback={<LoadingBlock label="正在加载活动…" />}>
        <EventList searchParams={searchParams} />
      </Suspense>
    </div>
  );
}

async function EventList({ searchParams }: { searchParams: PageProps<"/events">["searchParams"] }) {
  const params = await searchParams;
  const rawPage = Number(params.page ?? "1");
  // 用户可能手改 URL 成 ?page=abc 或 ?page=-3，后端会因为参数非法直接 400。
  // 在这里收敛成合法值，让页面永远能渲染出内容而不是一个错误页。
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) : 1;

  const result = await serverGet<PageResponse<EventResponse>>(
    // 后端的分页是从 0 开始的，URL 里的页码从 1 开始——只在这一处做转换，避免两套编号到处跑
    `/events?page=${page - 1}&size=${PAGE_SIZE}&sort=startAt`,
  );

  if (result === null) {
    return (
      <EmptyState
        title="暂时无法加载活动列表"
        description="后端服务当前不可达，请稍后重试。"
      />
    );
  }

  if (result.items.length === 0) {
    return (
      <EmptyState
        title={page > 1 ? "这一页没有活动了" : "还没有已发布的活动"}
        description={page > 1 ? undefined : "成为第一个组织者，创建一个活动吧。"}
        action={
          page > 1 ? (
            <Link href="/events">
              <Button variant="secondary">回到第一页</Button>
            </Link>
          ) : (
            <Link href="/organizer/events/new">
              <Button>创建活动</Button>
            </Link>
          )
        }
      />
    );
  }

  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {result.items.map((event) => (
          <EventCard key={event.id} event={event} />
        ))}
      </div>

      <nav className="mt-8 flex items-center justify-between text-sm">
        {page > 1 ? (
          <Link
            href={`/events?page=${page - 1}`}
            className="text-slate-600 hover:text-slate-900"
          >
            ← 上一页
          </Link>
        ) : (
          <span />
        )}
        <span className="text-slate-400">
          第 {result.page + 1} / {Math.max(result.totalPages, 1)} 页 · 共{" "}
          {result.totalElements} 个活动
        </span>
        {result.hasNext ? (
          <Link
            href={`/events?page=${page + 1}`}
            className="text-slate-600 hover:text-slate-900"
          >
            下一页 →
          </Link>
        ) : (
          <span />
        )}
      </nav>
    </>
  );
}
