package com.eventhub.api.auth.service;

import com.eventhub.api.auth.AuthCookieProperties;
import com.eventhub.api.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 刷新令牌的 Cookie 读写。
 *
 * <p>为什么用 HttpOnly Cookie 而不是让前端把刷新令牌存进 localStorage：
 * localStorage 里的任何东西都能被任意一段注入的 JavaScript 读走，
 * 一次 XSS 就等于刷新令牌泄露。HttpOnly 的 Cookie 对 JS 不可见，
 * 同样的 XSS 拿不到它。访问令牌仍然是内存里的普通字符串——
 * 它寿命只有 15 分钟、且刷新时会换新，风险窗口小得多。
 *
 * <p>代价是需要考虑 CSRF。这里的缓解是三重：Cookie 限定路径、
 * SameSite=Lax、以及响应体本身受 CORS 保护（跨站读不到新签发的访问令牌）。
 */
@Service
@RequiredArgsConstructor
public class AuthCookieService {

    private final AuthCookieProperties properties;
    private final JwtTokenProvider tokenProvider;

    /** 下发刷新令牌 Cookie。登录、注册、刷新成功后调用。 */
    public void write(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(properties.name(), refreshToken)
                .httpOnly(true)
                .secure(properties.secure())
                .path(properties.path())
                .sameSite(properties.sameSite())
                .maxAge(tokenProvider.refreshTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 清除 Cookie。登出时调用。 */
    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(properties.name(), "")
                .httpOnly(true)
                .secure(properties.secure())
                .path(properties.path())
                .sameSite(properties.sameSite())
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String cookieName() {
        return properties.name();
    }
}
