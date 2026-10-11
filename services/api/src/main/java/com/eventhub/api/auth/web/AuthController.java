package com.eventhub.api.auth.web;

import com.eventhub.api.auth.dto.LoginRequest;
import com.eventhub.api.auth.dto.RefreshRequest;
import com.eventhub.api.auth.dto.RegisterRequest;
import com.eventhub.api.auth.dto.TokenResponse;
import com.eventhub.api.auth.service.AuthCookieService;
import com.eventhub.api.auth.service.AuthService;
import com.eventhub.api.common.api.ApiResponse;
import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.exception.ErrorCode;
import com.eventhub.api.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>刷新令牌同时通过两个渠道下发：响应体（供移动端使用）和 HttpOnly Cookie
 * （供 Web 端使用）。移动端没有 Cookie 支持，Web 端则不该把刷新令牌暴露给 JS。
 * 两条路径共用同一套令牌，只是载体不同。
 */
@Tag(name = "认证", description = "注册、登录、刷新、登出")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @Operation(summary = "注册", description = "注册成功后直接签发令牌，无需再调登录")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                               HttpServletResponse response) {
        TokenResponse tokens = authService.register(request);
        authCookieService.write(response, tokens.refreshToken());
        return ApiResponse.ok(tokens);
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletResponse response) {
        TokenResponse tokens = authService.login(request);
        authCookieService.write(response, tokens.refreshToken());
        return ApiResponse.ok(tokens);
    }

    @Operation(summary = "刷新令牌",
            description = "轮换式刷新：旧 refreshToken 立即失效。Web 端从 Cookie 读取，移动端从请求体读取")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            @CookieValue(name = "${app.auth.cookie.name:eh_refresh}", required = false) String cookieToken,
            HttpServletResponse response) {

        // 请求体优先：显式传了就按显式传的来
        String refreshToken = firstNonBlank(
                request == null ? null : request.refreshToken(), cookieToken);

        if (refreshToken == null) {
            throw BusinessException.of(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        TokenResponse tokens = authService.refresh(refreshToken);
        authCookieService.write(response, tokens.refreshToken());
        return ApiResponse.ok(tokens);
    }

    @Operation(summary = "登出", description = "拉黑当前 access token、吊销全部 refresh token，并清除 Cookie")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletResponse response) {
        authService.logout(stripBearer(authorization), SecurityUtils.requireUserId());
        authCookieService.clear(response);
        return ApiResponse.ok();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    private static String stripBearer(String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
