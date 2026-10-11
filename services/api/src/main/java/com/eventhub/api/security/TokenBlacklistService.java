package com.eventhub.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 访问令牌黑名单，用于「登出后旧 access token 立即失效」。
 *
 * <p>只存 jti，TTL 设为令牌剩余寿命——过期后 Redis 自动清理，
 * 黑名单不会无限增长。Redis 不可用时<b>放行</b>（见 {@link #isBlacklisted}）：
 * 登出的强一致交给刷新令牌吊销来兜底，可用性优先。
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redis;

    public void blacklist(String jti, Duration ttl) {
        if (jti == null || jti.isBlank() || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
