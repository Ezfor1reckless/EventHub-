import Link from "next/link";
import { Suspense } from "react";
import { serverGet } from "@/lib/api/server";
import { EventCard } from "@/components/event-card";
import { Button } from "@/components/ui/button";
import { EmptyState, LoadingBlock } from "@/components/ui/feedback";
import type { EventResponse, PageResponse } from "@/lib/api/types";

/**
 * 首页是 Server Component：内容在服务端渲染好再发出去，
 * 用户不需要等 JS 加载完才能看到活动列表。
 *
 * 取数部分单独包在 <Suspense> 里。Next 16 的 cacheComponents 要求如此——
 * 好处是页面骨架先流式送达，活动列表晚几十毫秒补上，
 * 而不是整页空白等到数据就绪。上面那段介绍文字是静态的，立刻可见。
 */
export default function HomePage() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <section className="mb-10">
        <h1 className="text-3xl font-semibold tracking-tight text-slate-900">
          发现身边的社区活动
        </h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          浏览活动、在线报名，满员自动进入候补；活动当天扫码签到，
          有名额空出时会第一时间通知你。
        </p>
        <div className="mt-6 flex gap-3">
          <Link href="/events">
            <Button>浏览全部活动</Button>
          </Link>
          <Link href="/organizer/events/new">
            <Button variant="secondary">我要办活动</Button>
          </Link>
        </div>
      </section>

      <section>
        <div className="mb-4 flex items-baseline justify-between">
          <h2 className="text-lg font-semibold text-slate-900">近期活动</h2>
          <Link href="/events" className="text-sm text-slate-500 hover:text-slate-900">
            查看全部 →
          </Link>
        </div>
        <Suspense fallback={<LoadingBlock label="正在加载活动…" />}>
          <UpcomingEvents />
        </Suspense>
      </section>
    </div>
  );
}

async function UpcomingEvents() {
  const page = await serverGet<PageResponse<EventResponse>>("/events?size=6");

  if (page === null) {
    // 后端不可用。给出可操作的提示，而不是白屏或「出错了」三个字。
    return (
      <EmptyState
        title="暂时无法加载活动列表"
        description="后端服务当前不可达，请确认 API 已启动后刷新页面。"
      />
    );
  }

  if (page.items.length === 0) {
    return (
      <EmptyState
        title="还没有已发布的活动"
        description="成为第一个组织者，创建一个活动吧。"
        action={
          <Link href="/organizer/events/new">
            <Button>创建活动</Button>
          </Link>
        }
      />
    );
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {page.items.map((event) => (
        <EventCard key={event.id} event={event} />
      ))}
    </div>
  );
}
