/**
 * Matomo 埋点（HTTP Tracking API）。
 *
 * <p>移动端没有浏览器环境，用不了 Matomo 的 matomo.js；改为直接向
 * `/matomo.php` 发 GET 请求。所有调用都是「发出即忘」，不 await、不重试、
 * 失败也不上报——埋点是观测手段，不能因为分析服务不可达而影响报名。
 *
 * <p>未配置 `EXPO_PUBLIC_MATOMO_URL` 时整体空转，开发环境无需部署 Matomo。
 *
 * <p>已知局限：没有发送稳定的 `_id`（访客标识），所以 Matomo 会把每次上报
 * 当成新访客，「独立访客数」不准。事件计数不受影响。要修的话需要把访客 id
 * 持久化到 SecureStore，当前没做。
 */

const MATOMO_URL = process.env.EXPO_PUBLIC_MATOMO_URL;
const SITE_ID = process.env.EXPO_PUBLIC_MATOMO_SITE_ID;

function send(params: Record<string, string | number | undefined>): void {
  if (!MATOMO_URL || !SITE_ID) return;

  try {
    const query = new URLSearchParams({
      idsite: SITE_ID,
      rec: '1',
      apiv: '1',
    });
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) query.set(key, String(value));
    }

    // 不 await：调用方不该为了埋点等待一个网络往返
    void fetch(`${MATOMO_URL}/matomo.php?${query.toString()}`, {
      method: 'GET',
    }).catch(() => {
      // 刻意吞掉
    });
  } catch {
    // 构造 URL 都可能失败（配置里放了非法字符），同样不该影响主流程
  }
}

export function trackScreen(path: string, title?: string): void {
  send({ url: path, action_name: title });
}

export function trackEvent(
  category: string,
  action: string,
  name?: string,
  value?: number,
): void {
  send({ e_c: category, e_a: action, e_n: name, e_v: value });
}

/** 关键业务动作的语义化封装，与 Web 端保持同一套动作名。 */
export const analytics = {
  viewEvent: (eventId: number, status: string) =>
    trackEvent('Event', 'view_event', `${eventId}:${status}`),

  /** 确认和候补分开记——两者的转化含义完全不同。 */
  registerEvent: (eventId: number, status: string) =>
    trackEvent('Event', 'register_event', `${eventId}:${status}`),

  cancelRegistration: (eventId: number) =>
    trackEvent('Event', 'cancel_registration', String(eventId)),

  checkInSuccess: (eventId: number, method: string) =>
    trackEvent('Event', 'checkin_success', `${eventId}:${method}`),
};
