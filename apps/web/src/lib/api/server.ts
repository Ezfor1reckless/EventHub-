/**
 * 服务端数据获取，供 Server Components 使用。
 *
 * <p>服务端 fetch 无法携带用户的访问令牌，所以这里只能调用公开接口
 * （活动列表、活动详情）。需要身份的调用一律走客户端 {@code api} 客户端。
 *
 * <p>返回 null 而不是抛异常：Server Component 里抛出异常会渲染整个错误页，
 * 而「后端暂时不可用」更应该表现为页面上的一个提示，而不是白屏。
 */

const SERVER_API_BASE =
  process.env.API_INTERNAL_URL ??
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  "http://localhost:8080/api/v1";

export async function serverGet<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(`${SERVER_API_BASE}${path}`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      return null;
    }
    const envelope = (await response.json()) as { success: boolean; data: T };
    return envelope.success ? envelope.data : null;
  } catch {
    return null;
  }
}
