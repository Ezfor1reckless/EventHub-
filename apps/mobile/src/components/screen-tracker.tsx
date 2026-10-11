import { usePathname } from 'expo-router';
import { useEffect } from 'react';

import { trackScreen } from '@/lib/analytics/matomo';

/**
 * 路由变化时上报页面浏览。
 *
 * <p>Expo Router 是客户端路由，屏幕切换不会重新加载应用，
 * 所以 Matomo 收不到任何自动的页面浏览——不显式上报的话，
 * 整个 App 在统计里就只有启动时那一次访问。
 */
export function ScreenTracker() {
  const pathname = usePathname();

  useEffect(() => {
    // 路由参数会被排除在外，否则 /events/1、/events/2 会被当成两个页面，
    // 统计里全是长尾。要看具体活动用 view_event 事件，那才是业务维度。
    trackScreen(pathname);
  }, [pathname]);

  return null;
}
