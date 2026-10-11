package com.eventhub.api.common.ratelimit;

import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的固定窗口限流。
 *
 * <p>用 Lua 脚本把 INCR 和 PEXPIRE 合成一次原子调用。分成两条命令会有一个缺口：
 * 进程若在 INCR 之后、EXPIRE 之前挂掉，这个键就永远不会过期，对应的调用方
 * 会被永久限流——而且不会有任何报错提示你。
 *
 * <p>已知局限：固定窗口在窗口交界处允许最多 2 倍突发。对本项目的用途
 * （挡住暴力破解和脚本刷单）足够了，不值得为滑动窗口引入 ZSET 的额外开销。
 */
@Slf4j
@Service
public class RateLimitService {

    private static final String KEY_PREFIX = "rate:";

    /** 原子地自增并（仅首次）设置过期时间，返回自增后的值。 */
    private static final RedisScript<Long> INCR_WITH_TTL = new org.springframework.data.redis.core.script.DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class);

    private final StringRedisTemplate redis;

    /**
     * 关掉后 {@link #check} 直接返回。
     *
     * <p>集成测试必须关掉：所有测试都从 127.0.0.1 发起，而测试会创建几十个用户，
     * 任何有意义的人均限流都会把测试本身挡住。限流逻辑由
     * {@code RateLimitServiceIT} 用小限额单独验证。
     */
    private final boolean enabled;

    public RateLimitService(StringRedisTemplate redis,
                            @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    /**
     * 计数并检查限额，超限抛 {@link ErrorCode#TOO_MANY_REQUESTS}。
     *
     * <p><b>Redis 故障时放行（fail-open）并记 WARN。</b>
     * 判据是「Redis 里存的是不是唯一的事实来源」：限流只是保护性优化，
     * 不是业务事实，Redis 挂掉时把整个登录入口一起打挂不划算。
     * 反过来，签到令牌校验就必须 fail-closed——那里 Redis 是唯一的事实来源，
     * 放行等于让无法验证的令牌通过。两处的取舍不同，是有意为之。
     */
    public void check(String scope, String subject, int limit, Duration window) {
        if (!enabled) {
            return;
        }
        if (subject == null || subject.isBlank()) {
            // 拿不到限流主体（例如无请求上下文）就不限流，总好过把所有人算作同一个主体
            return;
        }

        String key = KEY_PREFIX + scope + ":" + subject;
        try {
            Long current = redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
            if (current != null && current > limit) {
                log.warn("触发限流 scope={} subject={} count={} limit={}", scope, subject, current, limit);
                throw BusinessException.of(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.warn("限流检查失败，放行本次请求 scope={} 原因={}", scope, ex.getMessage());
        }
    }
}
