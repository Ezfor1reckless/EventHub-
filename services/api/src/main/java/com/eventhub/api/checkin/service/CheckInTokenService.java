package com.eventhub.api.checkin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 动态签到码的发放与校验。
 *
 * <p>令牌是「时间段令牌」而不是「一次性令牌」：组织者投屏一个二维码，60 秒内
 * 在场的多个人都要扫同一个码，所以它必须能被重复使用。防止的是<b>过期码被复用</b>
 * （截图外传后过了很久才拿来用），而不是「一个码只能用一次」。
 * 「同一人不能签到两次」由报名状态和 {@code check_ins} 的唯一索引保证。
 *
 * <p>TTL 到期由 Redis 自动清理，不需要额外的清扫任务。
 */
@Service
@RequiredArgsConstructor
public class CheckInTokenService {

    private static final String KEY_PREFIX = "checkin:session:";
    private static final Duration TOKEN_TTL = Duration.ofSeconds(60);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    /** 为活动签发一个签到码，返回令牌与其有效期。 */
    public IssuedToken issue(Long eventId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redis.opsForValue().set(KEY_PREFIX + token, String.valueOf(eventId), TOKEN_TTL);
        return new IssuedToken(token, TOKEN_TTL);
    }

    /**
     * 令牌换活动 id。过期或不存在的令牌返回空。
     *
     * <p>Redis 不可用时这里会抛异常，最终变成 500。这是刻意的——签到码校验
     * 必须保守，宁可报错也不能放行一个无法验证的令牌。
     */
    public Optional<Long> resolveEventId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String raw = redis.opsForValue().get(KEY_PREFIX + token);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(raw));
        } catch (NumberFormatException ex) {
            // 键被写坏了，当作无效令牌处理
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Duration ttl) {
    }
}
