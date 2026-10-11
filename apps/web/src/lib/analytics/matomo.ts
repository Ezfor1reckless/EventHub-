/**
 * Matomo 埋点。
 *
 * <p><b>所有调用都不会抛异常、不会阻塞渲染、也不会重试。</b>
 * 埋点是观测手段，不是业务功能：Matomo 挂掉、被墙、脚本加载超时，
 * 都不应该让用户报不了名。这是刻意的 fail-silent——
 * 与后端鉴权那种必须 fail-closed 的场景正好相反。
 *
 * <p>未配置 {@code NEXT_PUBLIC_MATOMO_URL} 时整体空转，开发环境无需部署 Matomo。
 */

const MATOMO_URL = process.env.NEXT_PUBLIC_MATOMO_URL;
const MATOMO_SITE_ID = process.env.NEXT_PUBLIC_MATOMO_SITE_ID;

type MatomoCommand = unknown[];

declare global {
  interface Window {
    _paq?: MatomoCommand[];
  }
}

let scriptInjected = false;

/** 注入 Matomo 跟踪脚本。幂等，重复调用无副作用。 */
export function initMatomo(): void {
  if (typeof window === "undefined") return;
  if (scriptInjected) return;
  if (!MATOMO_URL || !MATOMO_SITE_ID) return;

  try {
    window._paq = window._paq ?? [];
    window._paq.push(["enableLinkTracking"]);
    window._paq.push(["setTrackerUrl", `${MATOMO_URL}/matomo.php`]);
    window._paq.push(["setSiteId", MATOMO_SITE_ID]);

    const script = document.createElement("script");
    script.async = true;
    script.defer = true;
    script.src = `${MATOMO_URL}/matomo.js`;
    // 脚本加载失败（网络不通、被拦截）时什么都不做。
    // 不加这个 handler 的话控制台会出现未捕获错误，看起来像应用出了问题。
    script.onerror = () => {};
    document.head.appendChild(script);

    scriptInjected = true;
  } catch {
    // 注入失败不影响任何业务功能
  }
}

function push(command: MatomoCommand): void {
  try {
    if (typeof window === "undefined") return;
    window._paq = window._paq ?? [];
    window._paq.push(command);
  } catch {
    // 刻意吞掉
  }
}

export function trackPageView(path: string, title?: string): void {
  push(["setCustomUrl", path]);
  if (title) push(["setDocumentTitle", title]);
  push(["trackPageView"]);
}

/**
 * 跟踪一个关键动作。
 *
 * <p>当前用到的动作名（与后端关键事件对齐）：
 * {@code view_event}、{@code register_event}、{@code cancel_registration}、{@code checkin_success}
 */
export function trackEvent(
  category: string,
  action: string,
  name?: string,
  value?: number,
): void {
  push(["trackEvent", category, action, name, value]);
}

/** 关键业务动作的语义化封装，避免各页面各写一套字符串。 */
export const analytics = {
  viewEvent: (eventId: number, title: string) =>
    trackEvent("Event", "view_event", `${eventId}:${title}`),

  /** 把报名结果是确认还是候补一并带上——两者的转化含义完全不同。 */
  registerEvent: (eventId: number, status: string) =>
    trackEvent("Event", "register_event", `${eventId}:${status}`),

  cancelRegistration: (eventId: number) =>
    trackEvent("Event", "cancel_registration", String(eventId)),

  checkInSuccess: (eventId: number, method: string) =>
    trackEvent("Event", "checkin_success", `${eventId}:${method}`),
};
