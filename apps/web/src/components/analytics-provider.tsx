"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { initMatomo, trackPageView } from "@/lib/analytics/matomo";

/**
 * 注入 Matomo 并在路由变化时上报页面浏览。
 *
 * <p>App Router 是客户端路由，页面不会真的重新加载，
 * 所以 Matomo 的自动 pageview 只会在首次进入时触发一次；
 * 后续的路由切换必须手动上报，否则整个应用在 Matomo 里只有一次访问。
 */
export function AnalyticsProvider() {
  const pathname = usePathname();

  useEffect(() => {
    initMatomo();
  }, []);

  useEffect(() => {
    trackPageView(pathname, document.title);
  }, [pathname]);

  return null;
}
