package com.eventhub.api.auth.web;

import com.eventhub.api.auth.dto.LoginRequest;
import com.eventhub.api.auth.dto.RefreshRequest;
import com.eventhub.api.auth.dto.RegisterRequest;
import com.eventhub.api.auth.dto.TokenResponse;
import com.eventhub.api.auth.service.AuthService;
import com.eventhub.api.common.api.ApiResponse;
import com.eventhub.api.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证", description = "注册、登录、刷新、登出")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    @Operation(summary = "注册", description = "注册成功后直接签发令牌，无需再调登录")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "刷新令牌", description = "轮换式刷新：旧 refreshToken 立即失效，返回新的一对")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "登出", description = "拉黑当前 access token 并吊销该用户全部 refresh token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(stripBearer(authorization), SecurityUtils.requireUserId());
        return ApiResponse.ok();
    }

    private static String stripBearer(String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
