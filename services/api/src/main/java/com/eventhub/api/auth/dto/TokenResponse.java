package com.eventhub.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "令牌响应")
public record TokenResponse(

        @Schema(description = "访问令牌，放在 Authorization: Bearer <token>") String accessToken,

        @Schema(description = "刷新令牌，只在此刻返回一次明文；服务端仅存哈希") String refreshToken,

        @Schema(description = "固定为 Bearer") String tokenType,

        @Schema(description = "访问令牌剩余有效秒数") long expiresIn
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
