package com.eventhub.api.common;

import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.ratelimit.RateLimitService;
import com.eventhub.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 限流逻辑单独测试。
 *
 * <p>集成测试的配置里限流是关闭的（否则测试自己就会被挡住），所以这里直接用真实
 * Redis 构造一个启用状态的实例，把限额压到很小的值来验证。
 */
class RateLimitServiceIT extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    private RateLimitService enabled() {
        return new RateLimitService(redis, true);
    }

    private RateLimitService disabled() {
        return new RateLimitService(redis, false);
    }

    @Test
    @DisplayName("超过限额抛出 TOO_MANY_REQUESTS")
    void exceeding_the_limit_is_rejected() {
        RateLimitService limiter = enabled();
        String subject = uniqueSubject();

        // 限额 2：前两次放行
        assertThatCode(() -> limiter.check("test-scope", subject, 2, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("test-scope", subject, 2, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();

        // 第三次拒绝
        assertThatThrownBy(() -> limiter.check("test-scope", subject, 2, Duration.ofMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求过于频繁");
    }

    @Test
    @DisplayName("不同主体各自计数，互不影响")
    void different_subjects_are_counted_separately() {
        RateLimitService limiter = enabled();
        String alice = uniqueSubject();
        String bob = uniqueSubject();

        limiter.check("test-scope", alice, 1, Duration.ofMinutes(1));
        assertThatThrownBy(() -> limiter.check("test-scope", alice, 1, Duration.ofMinutes(1)))
                .isInstanceOf(BusinessException.class);

        // bob 有自己的额度，不应被 alice 的用量影响
        assertThatCode(() -> limiter.check("test-scope", bob, 1, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("不同 scope 各自计数")
    void different_scopes_are_counted_separately() {
        RateLimitService limiter = enabled();
        String subject = uniqueSubject();

        limiter.check("scope-a", subject, 1, Duration.ofMinutes(1));
        assertThatCode(() -> limiter.check("scope-b", subject, 1, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("关闭后完全不限制")
    void disabled_limiter_never_rejects() {
        RateLimitService limiter = disabled();
        String subject = uniqueSubject();

        for (int i = 0; i < 50; i++) {
            assertThatCode(() -> limiter.check("test-scope", subject, 1, Duration.ofMinutes(1)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("窗口过期后额度重置")
    void window_expiry_resets_the_counter() throws Exception {
        RateLimitService limiter = enabled();
        String subject = uniqueSubject();

        limiter.check("test-scope", subject, 1, Duration.ofSeconds(1));
        assertThatThrownBy(() -> limiter.check("test-scope", subject, 1, Duration.ofSeconds(1)))
                .isInstanceOf(BusinessException.class);

        Thread.sleep(1200);

        assertThatCode(() -> limiter.check("test-scope", subject, 1, Duration.ofSeconds(1)))
                .as("窗口过期后应重新放行")
                .doesNotThrowAnyException();
    }

    private static String uniqueSubject() {
        return "test-" + UUID.randomUUID();
    }
}
