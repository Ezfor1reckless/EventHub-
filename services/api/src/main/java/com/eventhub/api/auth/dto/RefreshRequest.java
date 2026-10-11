package com.eventhub.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 刷新令牌请求。
 *
 * <p>字段可空是刻意的：Web 端把刷新令牌放在 HttpOnly Cookie 里，请求体会是空的；
 * 移动端没有 Cookie 支持，仍然在请求体里传。两种渠道共用同一个接口，
 * 由 Controller 决定用哪一个（见 {@code AuthController#refresh}）。
 */
@Schema(description = "刷新令牌请求。Web 端可留空，令牌从 Cookie 读取")
public record RefreshRequest(
        @Schema(description = "移动端传入；Web 端留空并用 Cookie") String refreshToken
) {
}
