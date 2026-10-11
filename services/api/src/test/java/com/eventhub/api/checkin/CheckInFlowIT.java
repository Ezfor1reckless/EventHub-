package com.eventhub.api.checkin;

import com.eventhub.api.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckInFlowIT extends AbstractIntegrationTest {

    private static final String SESSION_KEY_PREFIX = "checkin:session:";

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("组织者生成签到码，已报名用户扫码签到成功")
    void scan_checks_in_a_confirmed_registration() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);
        registerForEvent(user, eventId);

        String token = openSession(organizer, eventId);

        mvc.perform(post("/api/v1/checkins/scan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanBody(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("QR_SCAN"))
                .andExpect(jsonPath("$.data.ticketCode").isNotEmpty())
                .andExpect(jsonPath("$.data.checkedInAt").isNotEmpty());
    }

    @Test
    @DisplayName("同一个签到码可被多人扫描——它是时间段令牌，不是一次性令牌")
    void one_qr_code_serves_the_whole_room() throws Exception {
        String organizer = signUp("organizer");
        String alice = signUp("alice");
        String bob = signUp("bob");
        long eventId = createPublishedEvent(organizer, 10);
        registerForEvent(alice, eventId);
        registerForEvent(bob, eventId);

        String token = openSession(organizer, eventId);

        for (String user : new String[]{alice, bob}) {
            mvc.perform(post("/api/v1/checkins/scan")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scanBody(token)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("重复签到被拒绝")
    void duplicate_check_in_is_rejected() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);
        registerForEvent(user, eventId);
        String token = openSession(organizer, eventId);

        mvc.perform(post("/api/v1/checkins/scan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanBody(token)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/checkins/scan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanBody(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));
    }

    @Test
    @DisplayName("候补者不能签到")
    void waitlisted_user_cannot_check_in() throws Exception {
        String organizer = signUp("organizer");
        String seatHolder = signUp("seat");
        String queued = signUp("queued");
        long eventId = createPublishedEvent(organizer, 1);
        registerForEvent(seatHolder, eventId);
        registerForEvent(queued, eventId);

        String token = openSession(organizer, eventId);

        mvc.perform(post("/api/v1/checkins/scan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(queued))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanBody(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_NOT_CONFIRMED"));
    }

    @Test
    @DisplayName("没报名的人扫码返回 404")
    void unregistered_user_cannot_check_in() throws Exception {
        String organizer = signUp("organizer");
        String stranger = signUp("stranger");
        long eventId = createPublishedEvent(organizer, 10);

        String token = openSession(organizer, eventId);

        mvc.perform(post("/api/v1/checkins/scan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanBody(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGISTRATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("伪造的签到码被拒")
    void forged_token_is_rejected() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);
        registerForEvent(user, eventId);

        mvc.perform(post("/api/v1/checkins/scan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanBody("this-token-was-never-issued")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CHECKIN_TOKEN"));
    }

    @Test
    @DisplayName("签到码过期后失效")
    void expired_token_is_rejected() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);
        registerForEvent(user, eventId);

        String token = openSession(organizer, eventId);

        // 直接删掉 Redis 键来模拟 60 秒 TTL 到期，不必真的等一分钟
        redis.delete(SESSION_KEY_PREFIX + token);

        mvc.perform(post("/api/v1/checkins/scan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanBody(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CHECKIN_TOKEN"));
    }

    @Test
    @DisplayName("组织者可按票券码手工补签")
    void organizer_can_manually_check_in_by_ticket_code() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);
        registerForEvent(user, eventId);

        String ticketCode = ticketCodeOf(user);

        mvc.perform(post("/api/v1/events/" + eventId + "/checkins/manual")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketCode":"%s"}
                                """.formatted(ticketCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("MANUAL"))
                .andExpect(jsonPath("$.data.ticketCode").value(ticketCode));
    }

    @Test
    @DisplayName("别人不能用票券码替他人补签")
    void non_organizer_cannot_manually_check_in() throws Exception {
        String organizer = signUp("organizer");
        String alice = signUp("alice");
        String mallory = signUp("mallory");
        long eventId = createPublishedEvent(organizer, 10);
        registerForEvent(alice, eventId);

        String ticketCode = ticketCodeOf(alice);

        mvc.perform(post("/api/v1/events/" + eventId + "/checkins/manual")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mallory))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticketCode":"%s"}
                                """.formatted(ticketCode)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZER"));
    }

    @Test
    @DisplayName("普通用户不能生成签到码")
    void non_organizer_cannot_open_a_session() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);

        mvc.perform(post("/api/v1/events/" + eventId + "/checkin-session")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZER"));
    }

    @Test
    @DisplayName("签到统计反映确认数、签到数与签到率")
    void stats_reflect_reality() throws Exception {
        String organizer = signUp("organizer");
        String alice = signUp("alice");
        String bob = signUp("bob");
        long eventId = createPublishedEvent(organizer, 10);
        registerForEvent(alice, eventId);
        registerForEvent(bob, eventId);

        String token = openSession(organizer, eventId);

        // 只有 alice 签到
        mvc.perform(post("/api/v1/checkins/scan")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanBody(token)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/events/" + eventId + "/checkins")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedCount").value(2))
                .andExpect(jsonPath("$.data.checkedInCount").value(1))
                .andExpect(jsonPath("$.data.waitlistedCount").value(0))
                .andExpect(jsonPath("$.data.checkInRate").value(0.5));
    }

    // ---------- 辅助方法 ----------

    private String openSession(String organizerToken, long eventId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/events/" + eventId + "/checkin-session")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(60))
                .andReturn();
        return data(result).path("token").asText();
    }

    private String ticketCodeOf(String userToken) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/me/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = data(result).path("items");
        assertThat(items).isNotEmpty();
        return items.get(0).path("ticketCode").asText();
    }

    private static String scanBody(String token) {
        return """
                {"token":"%s"}
                """.formatted(token);
    }
}
