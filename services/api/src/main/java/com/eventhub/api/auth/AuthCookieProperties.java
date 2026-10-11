package com.eventhub.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 刷新令牌 Cookie 的配置。
 *
 * @param name      Cookie 名
 * @param path      Cookie 作用路径。默认收窄到 /api/v1/auth ——
 *                  刷新令牌没有理由出现在每一次业务请求里，路径越窄暴露面越小。
 * @param secure    是否只走 HTTPS。生产必须 true；本地 http 开发为 false。
 * @param sameSite  SameSite 策略。Lax 足以挡住跨站发起的刷新请求，
 *                  又不像 Strict 那样在从外部链接跳回时丢掉 Cookie。
 */
@ConfigurationProperties(prefix = "app.auth.cookie")
public record AuthCookieProperties(
        String name,
        String path,
        boolean secure,
        String sameSite
) {
    public AuthCookieProperties {
        if (name == null || name.isBlank()) {
            name = "eh_refresh";
        }
        if (path == null || path.isBlank()) {
            path = "/api/v1/auth";
        }
        if (sameSite == null || sameSite.isBlank()) {
            sameSite = "Lax";
        }
    }
}
