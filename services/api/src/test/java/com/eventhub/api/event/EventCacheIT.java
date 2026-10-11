package com.eventhub.api.event;

import com.eventhub.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 活动详情缓存的填充与失效。
 *
 * <p>缓存失效写错是那种「本地看着没问题、上线后用户看到已取消活动还在报名」的 bug，
 * 所以这里逐条验证每个写路径都会让缓存失效。
 */
class EventCacheIT extends AbstractIntegrationTest {

    private static final String KEY_PREFIX = "cache:event:detail:";

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("已发布活动的详情会被写入缓存")
    void published_event_detail_is_cached() throws Exception {
        String organizer = signUp("organizer");
        long eventId = createPublishedEvent(organizer, 10);

        mvc.perform(get("/api/v1/events/" + eventId)).andExpect(status().isOk());

        assertThat(redis.hasKey(KEY_PREFIX + eventId)).isTrue();
    }

    @Test
    @DisplayName("草稿不进缓存——它的可见性因人而异，缓存会导致越权")
    void draft_is_never_cached() throws Exception {
        String organizer = signUp("organizer");
        long eventId = createDraftEvent(organizer, 10);

        // 组织者本人能看到草稿
        mvc.perform(get("/api/v1/events/" + eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        assertThat(redis.hasKey(KEY_PREFIX + eventId))
                .as("草稿不可缓存，否则他人能绕过归属检查读到内容")
                .isFalse();
    }

    @Test
    @DisplayName("编辑活动会让缓存失效，后续读取拿到新值")
    void update_evicts_the_cache() throws Exception {
        String organizer = signUp("organizer");
        long eventId = createPublishedEvent(organizer, 10);

        mvc.perform(get("/api/v1/events/" + eventId)).andExpect(status().isOk());
        assertThat(redis.hasKey(KEY_PREFIX + eventId)).isTrue();

        String newTitle = "改过的标题";
        mvc.perform(put("/api/v1/events/" + eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody(newTitle, 10)))
                .andExpect(status().isOk());

        assertThat(redis.hasKey(KEY_PREFIX + eventId))
                .as("编辑后缓存必须失效").isFalse();

        mvc.perform(get("/api/v1/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(newTitle));
    }

    @Test
    @DisplayName("取消活动会让缓存失效——否则已取消的活动还会显示为可报名")
    void cancel_evicts_the_cache() throws Exception {
        String organizer = signUp("organizer");
        long eventId = createPublishedEvent(organizer, 10);

        mvc.perform(get("/api/v1/events/" + eventId)).andExpect(status().isOk());
        assertThat(redis.hasKey(KEY_PREFIX + eventId)).isTrue();

        mvc.perform(post("/api/v1/events/" + eventId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk());

        assertThat(redis.hasKey(KEY_PREFIX + eventId))
                .as("取消后缓存必须失效").isFalse();

        mvc.perform(get("/api/v1/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("发布活动会让缓存失效")
    void publish_evicts_the_cache() throws Exception {
        String organizer = signUp("organizer");
        long eventId = createDraftEvent(organizer, 10);

        publish(organizer, eventId);

        assertThat(redis.hasKey(KEY_PREFIX + eventId))
                .as("发布后缓存必须失效，否则草稿状态可能被沿用")
                .isFalse();
    }
}
