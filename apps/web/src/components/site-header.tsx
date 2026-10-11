"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useAuth, useIsOrganizer } from "@/lib/auth/auth-context";
import { api } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function SiteHeader() {
  const { user, status, logout } = useAuth();
  const isOrganizer = useIsOrganizer();
  const pathname = usePathname();
  const router = useRouter();

  const authenticated = status === "authenticated";

  // 未读数只在登录后查询；未登录时不去打这个接口
  const { data: unread } = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: api.me.unreadNotificationCount,
    enabled: authenticated,
    refetchInterval: 60_000,
  });

  const navLink = (href: string, label: string, badge?: number) => {
    const active = pathname === href || pathname.startsWith(`${href}/`);
    return (
      <Link
        key={href}
        href={href}
        className={cn(
          "relative rounded-md px-3 py-1.5 text-sm transition",
          active
            ? "bg-slate-100 font-medium text-slate-900"
            : "text-slate-600 hover:bg-slate-50 hover:text-slate-900",
        )}
      >
        {label}
        {badge && badge > 0 ? (
          <span className="ml-1.5 inline-flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 text-xs font-medium text-white">
            {badge > 99 ? "99+" : badge}
          </span>
        ) : null}
      </Link>
    );
  };

  return (
    <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4">
        <Link href="/" className="mr-2 text-base font-semibold text-slate-900">
          EventHub
        </Link>

        <nav className="flex items-center gap-1">
          {navLink("/events", "活动")}
          {authenticated ? navLink("/me/registrations", "我的报名") : null}
          {authenticated ? navLink("/me/notifications", "通知", unread) : null}
          {isOrganizer ? navLink("/organizer/events", "我组织的") : null}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          {status === "loading" ? (
            // 会话恢复期间先占位，避免「登录/注册」闪一下再变成用户名
            <span className="h-8 w-20 animate-pulse rounded-md bg-slate-100" />
          ) : authenticated ? (
            <>
              <Link
                href="/me"
                className="text-sm text-slate-600 hover:text-slate-900"
                title={user?.email}
              >
                {user?.nickname}
              </Link>
              <Button
                variant="ghost"
                size="sm"
                onClick={async () => {
                  await logout();
                  router.push("/");
                }}
              >
                登出
              </Button>
            </>
          ) : (
            <>
              <Link href="/login">
                <Button variant="ghost" size="sm">
                  登录
                </Button>
              </Link>
              <Link href="/register">
                <Button size="sm">注册</Button>
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
