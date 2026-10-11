"use client";

import Link from "next/link";
import { Suspense } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/auth-context";
import { RequireAuth } from "@/components/require-auth";
import { Badge } from "@/components/ui/badge";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { LoadingBlock } from "@/components/ui/feedback";
import { formatDateTime } from "@/lib/utils";

export default function ProfilePage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <RequireAuth>
        <Profile />
      </RequireAuth>
    </Suspense>
  );
}

function Profile() {
  const { user, logout } = useAuth();

  const { data: unread } = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: api.me.unreadNotificationCount,
  });

  if (!user) return null;

  return (
    <div className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">个人中心</h1>

      <Card>
        <CardHeader title="账号信息" />
        <CardBody>
          <dl className="space-y-3 text-sm">
            <div className="flex gap-4">
              <dt className="w-20 shrink-0 text-slate-400">邮箱</dt>
              <dd className="text-slate-900">{user.email}</dd>
            </div>
            <div className="flex gap-4">
              <dt className="w-20 shrink-0 text-slate-400">昵称</dt>
              <dd className="text-slate-900">{user.nickname}</dd>
            </div>
            <div className="flex gap-4">
              <dt className="w-20 shrink-0 text-slate-400">手机号</dt>
              <dd className="text-slate-900">{user.phone ?? "未填写"}</dd>
            </div>
            <div className="flex items-start gap-4">
              <dt className="w-20 shrink-0 pt-0.5 text-slate-400">角色</dt>
              <dd className="flex flex-wrap gap-1.5">
                {user.roles.map((role) => (
                  <Badge
                    key={role}
                    className={
                      role === "ADMIN"
                        ? "bg-purple-50 text-purple-700 ring-purple-200"
                        : role === "ORGANIZER"
                          ? "bg-sky-50 text-sky-700 ring-sky-200"
                          : undefined
                    }
                  >
                    {role === "ADMIN"
                      ? "管理员"
                      : role === "ORGANIZER"
                        ? "组织者"
                        : "普通用户"}
                  </Badge>
                ))}
              </dd>
            </div>
            <div className="flex gap-4">
              <dt className="w-20 shrink-0 text-slate-400">注册于</dt>
              <dd className="text-slate-900">{formatDateTime(user.createdAt)}</dd>
            </div>
          </dl>
        </CardBody>
      </Card>

      <div className="mt-6 grid gap-3 sm:grid-cols-3">
        <Link
          href="/me/registrations"
          className="rounded-lg border border-slate-200 bg-white p-4 text-center shadow-sm transition hover:border-slate-300"
        >
          <p className="text-sm font-medium text-slate-900">我的报名</p>
          <p className="mt-0.5 text-xs text-slate-500">查看票券与候补状态</p>
        </Link>
        <Link
          href="/me/notifications"
          className="rounded-lg border border-slate-200 bg-white p-4 text-center shadow-sm transition hover:border-slate-300"
        >
          <p className="text-sm font-medium text-slate-900">
            通知
            {unread ? (
              <span className="ml-1.5 inline-flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 text-xs text-white">
                {unread}
              </span>
            ) : null}
          </p>
          <p className="mt-0.5 text-xs text-slate-500">报名与递补提醒</p>
        </Link>
        <Link
          href="/organizer/events"
          className="rounded-lg border border-slate-200 bg-white p-4 text-center shadow-sm transition hover:border-slate-300"
        >
          <p className="text-sm font-medium text-slate-900">我组织的活动</p>
          <p className="mt-0.5 text-xs text-slate-500">报名名单与签到</p>
        </Link>
      </div>

      <button
        onClick={() => void logout()}
        className="mt-8 text-sm text-red-600 hover:underline"
      >
        退出登录
      </button>
    </div>
  );
}
