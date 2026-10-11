"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { RequireAuth } from "@/components/require-auth";
import { EventForm } from "@/components/event-form";
import { TicketQr } from "@/components/ticket-qr";
import { Button } from "@/components/ui/button";
import { EventStatusBadge, RegistrationStatusBadge } from "@/components/ui/badge";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Field, Input } from "@/components/ui/field";
import { EmptyState, ErrorAlert, LoadingBlock } from "@/components/ui/feedback";
import { cn, formatDateTime } from "@/lib/utils";

export default function ManageEventPage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <RequireAuth>
        <ManageEvent />
      </RequireAuth>
    </Suspense>
  );
}

type Tab = "registrations" | "checkin" | "edit";

function ManageEvent() {
  const params = useParams<{ id: string }>();
  const eventId = Number(params.id);
  const [tab, setTab] = useState<Tab>("registrations");

  const { data: event, isLoading, error } = useQuery({
    queryKey: ["event", eventId],
    queryFn: () => api.events.get(eventId),
    enabled: Number.isInteger(eventId) && eventId > 0,
  });

  if (isLoading) return <LoadingBlock />;

  if (error || !event) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-10">
        <ErrorAlert error={error ?? new Error("活动不存在")} />
        <Link href="/organizer/events" className="mt-4 inline-block text-sm underline">
          返回我的活动
        </Link>
      </div>
    );
  }

  const tabs: Array<{ key: Tab; label: string }> = [
    { key: "registrations", label: "报名名单" },
    { key: "checkin", label: "签到" },
    { key: "edit", label: "编辑活动" },
  ];

  return (
    <div className="mx-auto max-w-4xl px-4 py-10">
      <Link
        href="/organizer/events"
        className="text-sm text-slate-500 hover:text-slate-900"
      >
        ← 我组织的活动
      </Link>

      <div className="mt-3 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">{event.title}</h1>
          <p className="mt-1 text-sm text-slate-500">
            {formatDateTime(event.startAt)} · {event.location}
          </p>
        </div>
        <EventStatusBadge status={event.status} />
      </div>

      <div className="mt-4 grid grid-cols-3 gap-3">
        <StatTile label="已确认" value={`${event.confirmedCount} / ${event.capacity}`} />
        <StatTile label="候补中" value={String(event.waitlistedCount)} />
        <StatTile label="剩余名额" value={String(event.seatsRemaining)} />
      </div>

      <div className="mt-6 flex gap-1 border-b border-slate-200">
        {tabs.map((item) => (
          <button
            key={item.key}
            onClick={() => setTab(item.key)}
            className={cn(
              "-mb-px border-b-2 px-4 py-2 text-sm transition",
              tab === item.key
                ? "border-slate-900 font-medium text-slate-900"
                : "border-transparent text-slate-500 hover:text-slate-900",
            )}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="mt-6">
        {tab === "registrations" ? <RegistrationsTab eventId={eventId} /> : null}
        {tab === "checkin" ? <CheckInTab eventId={eventId} /> : null}
        {tab === "edit" ? (
          <Card>
            <CardHeader
              title="编辑活动"
              description="全量保存。已取消或已结束的活动不可编辑。"
            />
            <CardBody>
              <EventForm
                initial={event}
                submitLabel="保存修改"
                onSubmit={(payload) => api.events.update(eventId, payload)}
              />
            </CardBody>
          </Card>
        ) : null}
      </div>
    </div>
  );
}

function StatTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-4 py-3">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-0.5 text-lg font-medium text-slate-900">{value}</p>
    </div>
  );
}

function RegistrationsTab({ eventId }: { eventId: number }) {
  const queryClient = useQueryClient();
  const [actionError, setActionError] = useState<unknown>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ["registrations", "event", eventId],
    queryFn: () => api.registrations.listByEvent(eventId, { size: 100 }),
  });

  const cancelMutation = useMutation({
    mutationFn: (registrationId: number) => api.registrations.cancel(registrationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["registrations", "event", eventId] });
      queryClient.invalidateQueries({ queryKey: ["event", eventId] });
    },
    onError: setActionError,
  });

  if (isLoading) return <LoadingBlock />;
  if (error) return <ErrorAlert error={error} />;

  const items = data?.items ?? [];
  if (items.length === 0) {
    return <EmptyState title="还没有人报名" description="发布活动后用户就能看到并报名。" />;
  }

  return (
    <div className="space-y-3">
      {actionError ? <ErrorAlert error={actionError} /> : null}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-4 py-2 font-medium">用户</th>
              <th className="px-4 py-2 font-medium">状态</th>
              <th className="px-4 py-2 font-medium">票券码</th>
              <th className="px-4 py-2 font-medium">报名时间</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {items.map((registration) => (
              <tr key={registration.id}>
                <td className="px-4 py-2 text-slate-900">
                  用户 #{registration.id}
                </td>
                <td className="px-4 py-2">
                  <RegistrationStatusBadge status={registration.status} />
                </td>
                <td className="px-4 py-2 font-mono text-xs text-slate-600">
                  {registration.ticketCode}
                </td>
                <td className="px-4 py-2 text-slate-500">
                  {formatDateTime(registration.registeredAt)}
                </td>
                <td className="px-4 py-2 text-right">
                  {registration.status !== "CANCELLED" &&
                  registration.status !== "CHECKED_IN" ? (
                    <button
                      className="text-xs text-red-600 hover:underline"
                      disabled={cancelMutation.isPending}
                      onClick={() => {
                        if (window.confirm("确定取消这条报名吗？名额会立即递补给候补队列的队首。")) {
                          cancelMutation.mutate(registration.id);
                        }
                      }}
                    >
                      取消
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function CheckInTab({ eventId }: { eventId: number }) {
  const queryClient = useQueryClient();
  // 存「过期时刻」而不是「剩余秒数」：后者需要每秒 setState 一次来递减，
  // 而剩余时间是能从过期时刻推导出来的。少一份需要同步的状态，就少一类不同步的 bug。
  const [session, setSession] = useState<{ token: string; expiresAt: number } | null>(
    null,
  );
  const [now, setNow] = useState(() => Date.now());
  const [ticketCode, setTicketCode] = useState("");
  const [manualResult, setManualResult] = useState<string | null>(null);

  const { data: stats, error: statsError } = useQuery({
    queryKey: ["checkin", "stats", eventId],
    queryFn: () => api.checkin.stats(eventId),
    refetchInterval: 15_000,
  });

  const openSession = useMutation({
    mutationFn: () => api.checkin.openSession(eventId),
    onSuccess: (data) => {
      setSession({
        token: data.token,
        expiresAt: Date.now() + data.expiresInSeconds * 1000,
      });
    },
  });

  const manual = useMutation({
    mutationFn: (code: string) => api.checkin.manual(eventId, code),
    onSuccess: (result) => {
      setManualResult(`已为票券 ${result.ticketCode} 完成签到`);
      setTicketCode("");
      queryClient.invalidateQueries({ queryKey: ["checkin", "stats", eventId] });
      queryClient.invalidateQueries({ queryKey: ["registrations", "event", eventId] });
    },
  });

  // 签到码 60 秒过期。倒计时让组织者知道什么时候该刷新，
  // 而不是对着一张已经失效的二维码纳闷为什么没人扫成功。
  useEffect(() => {
    if (!session) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [session]);

  const remaining = session
    ? Math.max(0, Math.ceil((session.expiresAt - now) / 1000))
    : 0;
  // 过期后二维码自动消失，不需要额外的「清理」状态
  const active = session !== null && remaining > 0;
  const activeToken = active ? session.token : null;

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader
          title="动态签到码"
          description="投屏给现场用户扫描。60 秒后失效，可随时重新生成。"
        />
        <CardBody className="flex flex-col items-center gap-4">
          {activeToken ? (
            <>
              <TicketQr value={activeToken} size={200} alt="动态签到二维码" />
              <p className="text-sm text-slate-600">
                剩余 <span className="font-mono text-slate-900">{remaining}</span> 秒
              </p>
            </>
          ) : (
            <div className="flex size-[200px] items-center justify-center rounded border border-dashed border-slate-300 text-sm text-slate-400">
              {openSession.isPending
                ? "生成中…"
                : session
                  ? "已过期，请重新生成"
                  : "尚未生成"}
            </div>
          )}

          {openSession.error ? <ErrorAlert error={openSession.error} /> : null}

          <Button onClick={() => openSession.mutate()} disabled={openSession.isPending}>
            {activeToken ? "重新生成" : "生成签到码"}
          </Button>

          <p className="text-center text-xs text-slate-500">
            同一个码可以被现场所有人扫描。同一个人重复扫码会被拒绝。
          </p>
        </CardBody>
      </Card>

      <div className="space-y-6">
        <Card>
          <CardHeader title="签到统计" />
          <CardBody>
            {statsError ? (
              <ErrorAlert error={statsError} />
            ) : stats ? (
              <dl className="grid grid-cols-3 gap-3 text-center">
                <div>
                  <dt className="text-xs text-slate-500">已确认</dt>
                  <dd className="mt-1 text-xl font-medium text-slate-900">
                    {stats.confirmedCount}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-slate-500">已签到</dt>
                  <dd className="mt-1 text-xl font-medium text-sky-600">
                    {stats.checkedInCount}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-slate-500">签到率</dt>
                  <dd className="mt-1 text-xl font-medium text-slate-900">
                    {(stats.checkInRate * 100).toFixed(0)}%
                  </dd>
                </div>
              </dl>
            ) : (
              <LoadingBlock />
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="手工补签"
            description="用户手机没电或扫码失败时，按票券码补签。"
          />
          <CardBody>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (ticketCode.trim()) manual.mutate(ticketCode.trim());
              }}
              className="space-y-3"
            >
              <Field label="票券码" htmlFor="ticketCode">
                <Input
                  id="ticketCode"
                  value={ticketCode}
                  onChange={(e) => setTicketCode(e.target.value.toUpperCase())}
                  placeholder="例如 7K3M9PQR2XYZ"
                  className="font-mono"
                />
              </Field>
              <Button type="submit" disabled={manual.isPending || !ticketCode.trim()}>
                {manual.isPending ? "处理中…" : "补签"}
              </Button>
            </form>

            {manual.error ? (
              <div className="mt-3">
                <ErrorAlert error={manual.error} />
              </div>
            ) : null}
            {manualResult ? (
              <p className="mt-3 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                {manualResult}
              </p>
            ) : null}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
