package com.eventhub.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param secret     HMAC 密钥原文。启动时校验长度 ≥ 32 字节（HS256 的下限）。
 * @param accessTtl  访问令牌有效期，默认 15 分钟。
 * @param refreshTtl 刷新令牌有效期，默认 7 天。
 * @param issuer     JWT iss 声明。
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtl,
        String issuer
) {
    public JwtProperties {
        if (accessTtl == null) {
            accessTtl = Duration.ofMinutes(15);
        }
        if (refreshTtl == null) {
            refreshTtl = Duration.ofDays(7);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "eventhub";
        }
    }
}
