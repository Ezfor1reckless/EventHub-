package com.eventhub.api.security;

import com.eventhub.api.user.domain.Role;
import com.eventhub.api.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 访问令牌的签发与校验。
 *
 * <p>刷新令牌刻意<b>不是</b> JWT，而是随机不透明串（见 {@code AuthService}）——
 * 它需要能被服务端单独吊销，JWT 的自包含特性在这里反而是负担。
 */
@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;

        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret 未配置。请设置环境变量 JWT_SECRET（建议 openssl rand -base64 48）。");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret 太短：需至少 %d 字节，当前 %d 字节。".formatted(MIN_SECRET_BYTES, keyBytes.length));
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /** 签发访问令牌。jti 用于登出时加入 Redis 黑名单。 */
    public String createAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(Role::name).toList();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .issuer(properties.issuer())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验访问令牌（签名、过期、issuer 全部校验）。
     *
     * @throws JwtException 令牌非法或已过期
     */
    public Jws<Claims> parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token);
    }

    public Duration accessTtl() {
        return properties.accessTtl();
    }

    public Duration refreshTtl() {
        return properties.refreshTtl();
    }
}
