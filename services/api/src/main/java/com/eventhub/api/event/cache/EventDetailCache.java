package com.eventhub.api.event.cache;

import com.eventhub.api.event.dto.EventResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 活动详情缓存。
 *
 * <p><b>只缓存已发布的活动。</b> 草稿的可见性取决于调用者身份（组织者能看、别人 404），
 * 把草稿塞进缓存会让第二个访问者绕过归属检查直接拿到内容——这是缓存引入的越权漏洞，
 * 而且不会有任何报错。发布状态对所有人生效，缓存它才安全。
 *
 * <p>TTL 取 60 秒而不是策划里写的 5 分钟：缓存的值里含「剩余名额」，而报名随时在变，
 * 5 分钟会让用户看到一个明显过时的数字。更重要的是——<b>缓存只影响展示，不影响正确性</b>：
 * 名额是否真的还有，是在报名时于数据库行锁下重新判定的（见 RegistrationService#register）。
 * 所以再陈旧的缓存也不会造成超卖。
 *
 * <p>所有 Redis 异常都被吞掉并回落到数据库。缓存是优化，不该成为故障点。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDetailCache {

    private static final String KEY_PREFIX = "cache:event:detail:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public Optional<EventResponse> get(Long eventId) {
        try {
            String json = redis.opsForValue().get(key(eventId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, EventResponse.class));
        } catch (Exception ex) {
            log.warn("读取活动详情缓存失败，回落数据库 eventId={} 原因={}", eventId, ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(Long eventId, EventResponse response) {
        try {
            redis.opsForValue().set(key(eventId), objectMapper.writeValueAsString(response), TTL);
        } catch (Exception ex) {
            log.warn("写入活动详情缓存失败 eventId={} 原因={}", eventId, ex.getMessage());
        }
    }

    /**
     * 失效缓存。活动的任何变更（编辑、发布、取消）都必须调用它——
     * 否则一个已取消的活动会在缓存里继续「可报名」最多 60 秒。
     */
    public void evict(Long eventId) {
        try {
            redis.delete(key(eventId));
        } catch (Exception ex) {
            log.warn("清除活动详情缓存失败 eventId={} 原因={}", eventId, ex.getMessage());
        }
    }

    private static String key(Long eventId) {
        return KEY_PREFIX + eventId;
    }
}
