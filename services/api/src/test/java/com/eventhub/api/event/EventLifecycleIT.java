package com.eventhub.api.event;

import com.eventhub.api.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventLifecycleIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("草稿活动对外不可见：公开详情返回 404，组织者本人可见")
    void draft_is_private_until_published() throws Exception {
        String organizer = signUp("organizer");
        long eventId = createDraftEvent(organizer, 10);

        mvc.perform(get("/api/v1/events/" + eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

        mvc.perform(get("/api/v1/events/" + eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("发布后出现在公开列表中")
    void published_event_appears_in_public_list() throws Exception {
        String organizer = signUp("organizer");
        long eventId = createPublishedEvent(organizer, 10);

        MvcResult listed = mvc.perform(get("/api/v1/events").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = data(listed).path("items");
        boolean found = false;
        for (JsonNode item : items) {
            if (item.path("id").asLong() == eventId) {
                found = true;
                assertThat(item.path("status").asText()).isEqualTo("PUBLISHED");
            }
        }
        assertThat(found).as("已发布的活动应当出现在公开列表中").isTrue();
    }

    @Test
    @DisplayName("他人不能修改、发布或取消非自己组织的活动")
    void non_organizer_cannot_mutate() throws Exception {
        String organizer = signUp("organizer");
        String stranger = signUp("stranger");
        long eventId = createDraftEvent(organizer, 10);

        mvc.perform(put("/api/v1/events/" + eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody("被篡改的标题", 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZER"));

        mvc.perform(post("/api/v1/events/" + eventId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/events/" + eventId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("结束时间早于开始时间被拒，错误码明确")
    void invalid_time_range_is_rejected() throws Exception {
        String organizer = signUp("organizer");
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);

        mvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"时间颠倒","location":"某地",
                                 "startAt":"%s","endAt":"%s","capacity":10}
                                """.formatted(start, start.minus(1, ChronoUnit.HOURS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVENT_TIME_INVALID"));
    }

    @Test
    @DisplayName("容量非法被 Bean Validation 拦下")
    void capacity_must_be_positive() throws Exception {
        String organizer = signUp("organizer");

        mvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody("容量为零", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.capacity").exists());
    }

    @Test
    @DisplayName("首次创建活动后自动获得 ORGANIZER 角色")
    void first_event_grants_organizer_role() throws Exception {
        String organizer = signUp("newcomer");
        createDraftEvent(organizer, 5);

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                // 角色集合是无序的，用数组包含匹配而不是下标
                .andExpect(jsonPath("$.data.roles[?(@ == 'ORGANIZER')]").exists())
                .andExpect(jsonPath("$.data.roles[?(@ == 'USER')]").exists());
    }

    @Test
    @DisplayName("我组织的活动列表只含自己的活动")
    void my_events_are_scoped_to_me() throws Exception {
        String organizer = signUp("organizer");
        String other = signUp("other");
        long mine = createDraftEvent(organizer, 5);
        long theirs = createDraftEvent(other, 5);

        MvcResult listed = mvc.perform(get("/api/v1/me/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = data(listed).path("items");
        boolean hasMine = false;
        for (JsonNode item : items) {
            assertThat(item.path("id").asLong())
                    .as("不应看到他人组织的活动")
                    .isNotEqualTo(theirs);
            if (item.path("id").asLong() == mine) {
                hasMine = true;
            }
        }
        assertThat(hasMine).isTrue();
    }
}
