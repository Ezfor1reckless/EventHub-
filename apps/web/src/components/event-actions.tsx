"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { analytics } from "@/lib/analytics/matomo";
import { useAuth } from "@/lib/auth/auth-context";
import { Button } from "@/components/ui/button";
import { ErrorAlert, Spinner } from "@/components/ui/feedback";
import { RegistrationStatusBadge } from "@/components/ui/badge";
import type { EventStatus } from "@/lib/api/types";

/**
 * 活动详情页的报名操作区。
 *
 * 详情内容由 Server Component 渲染，但按钮状态取决于「当前用户在这个活动里的报名」，
 * 那是只有客户端才知道的信息（访问令牌在内存里），所以这部分必须下沉到客户端组件。
 */
export function EventActions({
  eventId,
  eventStatus,
}: {
  eventId: number;
  eventStatus: EventStatus;
}) {
  const { status: authStatus } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  // 活动详情是 Server Component 渲染的，页面浏览本身由 AnalyticsProvider 上报，
  // 但「看了哪个活动」这个业务维度只有这里知道
  useEffect(() => {
    analytics.viewEvent(eventId, eventStatus);
  }, [eventId, eventStatus]);

  const registrationQuery = useQuery({
    queryKey: ["registration", "mine", eventId],
    queryFn: () => api.registrations.findMine(eventId),
    enabled: authStatus === "authenticated",
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["registration", "mine", eventId] });
    queryClient.invalidateQueries({ queryKey: ["registrations", "mine"] });
    queryClient.invalidateQueries({ queryKey: ["notifications"] });
    // 名额数由服务端渲染，改了数据要让 RSC 重新取一次
    router.refresh();
  };

  const registerMutation = useMutation({
    mutationFn: () => api.registrations.create(eventId),
    onSuccess: (registration) => {
      // 确认和候补分开记：两者的转化含义完全不同
      analytics.registerEvent(eventId, registration.status);
      refresh();
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (registrationId: number) =>
      api.registrations.cancel(registrationId),
    onSuccess: () => {
      analytics.cancelRegistration(eventId);
      refresh();
    },
  });

  if (authStatus === "loading") {
    return (
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Spinner /> 正在确认登录状态…
      </div>
    );
  }

  if (authStatus === "anonymous") {
    return (
      <div className="space-y-2">
        <Link href={`/login?next=${encodeURIComponent(`/events/${eventId}`)}`}>
          <Button className="w-full">登录后报名</Button>
        </Link>
        <p className="text-xs text-slate-500">
          还没有账号？
          <Link href="/register" className="ml-1 text-slate-900 underline">
            立即注册
          </Link>
        </p>
      </div>
    );
  }

  if (registrationQuery.isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Spinner /> 正在查询报名状态…
      </div>
    );
  }

  const error = registerMutation.error ?? cancelMutation.error;
  const pending = registerMutation.isPending || cancelMutation.isPending;
  const registration = registrationQuery.data;

  return (
    <div className="space-y-3">
      {error ? <ErrorAlert error={error} /> : null}

      {registration ? (
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <RegistrationStatusBadge status={registration.status} />
            {registration.status === "WAITLISTED" ? (
              <span className="text-xs text-slate-500">
                名额空出后会按候补顺序自动递补
              </span>
            ) : null}
          </div>

          {registration.status === "CONFIRMED" ||
          registration.status === "CHECKED_IN" ? (
            <div className="rounded-md bg-slate-50 px-3 py-2">
              <p className="text-xs text-slate-500">票券码</p>
              <p className="font-mono text-sm tracking-wider text-slate-900">
                {registration.ticketCode}
              </p>
            </div>
          ) : null}
        </div>
      ) : null}

      {registration && registration.status !== "CANCELLED" ? (
        <Button
          variant="secondary"
          className="w-full"
          disabled={pending || registration.status === "CHECKED_IN"}
          onClick={() => cancelMutation.mutate(registration.id)}
        >
          {registration.status === "CHECKED_IN"
            ? "已签到，无法取消"
            : pending
              ? "处理中…"
              : registration.status === "WAITLISTED"
                ? "取消候补"
                : "取消报名"}
        </Button>
      ) : (
        <Button
          className="w-full"
          disabled={pending || eventStatus !== "PUBLISHED"}
          onClick={() => registerMutation.mutate()}
        >
          {eventStatus !== "PUBLISHED"
            ? "当前不可报名"
            : pending
              ? "处理中…"
              : registration
                ? "重新报名"
                : "立即报名"}
        </Button>
      )}

      <p className="text-xs text-slate-500">
        名额满时会自动进入候补队列，无需重复提交。
      </p>
    </div>
  );
}
