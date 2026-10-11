"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-context";
import { LoadingBlock } from "@/components/ui/feedback";

/**
 * 受保护页面的外壳：未登录时跳转到登录页，并记住原地址。
 *
 * <p>这只是 UX 层面的守卫，<b>不是安全边界</b>——真正的权限控制在后端每个接口上。
 * 把前端守卫当安全措施是常见的错误：任何人都能直接调接口。
 */
export function RequireAuth({
  children,
  organizerOnly = false,
}: {
  children: ReactNode;
  organizerOnly?: boolean;
}) {
  const { status, user } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const isOrganizer = Boolean(
    user?.roles?.includes("ORGANIZER") || user?.roles?.includes("ADMIN"),
  );
  const blocked = status === "authenticated" && organizerOnly && !isOrganizer;

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [status, router, pathname]);

  if (status === "loading") {
    return <LoadingBlock label="正在确认登录状态…" />;
  }

  if (status === "anonymous") {
    // 跳转已在进行中，先不渲染任何内容，避免闪出受保护内容
    return <LoadingBlock label="正在跳转到登录页…" />;
  }

  if (blocked) {
    return (
      <div className="mx-auto max-w-md px-4 py-16 text-center">
        <p className="text-sm text-slate-600">
          这个页面需要组织者权限。创建任意一个活动即可获得该角色。
        </p>
      </div>
    );
  }

  return <>{children}</>;
}
