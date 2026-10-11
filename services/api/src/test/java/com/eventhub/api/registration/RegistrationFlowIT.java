package com.eventhub.api.registration;

import com.eventhub.api.event.domain.Event;
import com.eventhub.api.event.repository.EventRepository;
import com.eventhub.api.registration.domain.RegistrationStatus;
import com.eventhub.api.registration.dto.RegistrationResponse;
import com.eventhub.api.registration.repository.RegistrationRepository;
import com.eventhub.api.registration.service.RegistrationService;
import com.eventhub.api.support.AbstractIntegrationTest;
import com.eventhub.api.user.domain.User;
import com.eventhub.api.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("名额充足时报名直接确认，并拿到票券码")
    void register_confirms_when_seats_available() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);

        mvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.ticketCode").isNotEmpty())
                .andExpect(jsonPath("$.data.event.id").value(eventId));
    }

    @Test
    @DisplayName("重复报名是幂等的：返回同一条记录，不重复占位")
    void register_is_idempotent() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("bob");
        long eventId = createPublishedEvent(organizer, 10);

        MvcResult first = mvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(data(second).path("id").asLong())
                .as("重复报名应返回同一条记录")
                .isEqualTo(data(first).path("id").asLong());
        assertThat(data(second).path("ticketCode").asText())
                .isEqualTo(data(first).path("ticketCode").asText());

        assertThat(registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("满员后报名自动进入候补，且顺序为先到先得")
    void register_waitlists_when_full() throws Exception {
        String organizer = signUp("organizer");
        String first = signUp("first");
        String second = signUp("second");
        long eventId = createPublishedEvent(organizer, 1);

        mvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.data.waitlistedAt").isNotEmpty());
    }

    @Test
    @DisplayName("有人取消后，候补队首自动递补为已确认")
    void cancel_promotes_head_of_waitlist() {
        User organizer = newUser("org");
        User confirmed = newUser("confirmed");
        User queued = newUser("queued");
        long eventId = publishedEvent(organizer.getId(), 1);

        RegistrationResponse confirmedReg = registrationService.register(confirmed.getId(), eventId);
        RegistrationResponse queuedReg = registrationService.register(queued.getId(), eventId);
        assertThat(confirmedReg.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(queuedReg.status()).isEqualTo(RegistrationStatus.WAITLISTED);

        registrationService.cancel(confirmed.getId(), confirmedReg.id());

        assertThat(reloadStatus(queuedReg.id()))
                .as("名额空出后候补队首应被递补")
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(reloadStatus(confirmedReg.id())).isEqualTo(RegistrationStatus.CANCELLED);
    }

    @Test
    @DisplayName("递补严格按进入候补的先后顺序，而不是按主键")
    void promotion_follows_arrival_order_not_id() {
        User organizer = newUser("org");
        User seatHolder = newUser("seat");
        User early = newUser("early");
        User late = newUser("late");
        long eventId = publishedEvent(organizer.getId(), 1);

        RegistrationResponse seat = registrationService.register(seatHolder.getId(), eventId);
        RegistrationResponse earlyReg = registrationService.register(early.getId(), eventId);
        RegistrationResponse lateReg = registrationService.register(late.getId(), eventId);

        registrationService.cancel(seatHolder.getId(), seat.id());

        assertThat(reloadStatus(earlyReg.id()))
                .as("先进入候补的应先被递补")
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(reloadStatus(lateReg.id()))
                .as("晚进入候补的应仍在候补")
                .isEqualTo(RegistrationStatus.WAITLISTED);
    }

    @Test
    @DisplayName("候补者取消不影响名额，也不会触发多余的递补")
    void cancelling_a_waitlisted_registration_frees_nothing() {
        User organizer = newUser("org");
        User seatHolder = newUser("seat");
        User queued = newUser("queued");
        long eventId = publishedEvent(organizer.getId(), 1);

        RegistrationResponse seat = registrationService.register(seatHolder.getId(), eventId);
        RegistrationResponse queuedReg = registrationService.register(queued.getId(), eventId);

        registrationService.cancel(queued.getId(), queuedReg.id());

        assertThat(reloadStatus(queuedReg.id())).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(reloadStatus(seat.id()))
                .as("名额持有者不应受影响")
                .isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("本人不能取消他人的报名")
    void cannot_cancel_someone_elses_registration() {
        User organizer = newUser("org");
        User alice = newUser("alice");
        User mallory = newUser("mallory");
        long eventId = publishedEvent(organizer.getId(), 5);

        RegistrationResponse aliceReg = registrationService.register(alice.getId(), eventId);

        // 返回「不存在」而不是「无权限」，避免探测报名 id 是否存在
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> registrationService.cancel(mallory.getId(), aliceReg.id()))
                .hasMessageContaining("报名记录不存在");

        assertThat(reloadStatus(aliceReg.id())).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("并发报名不超卖：容量 5，20 人同时抢，恰好 5 确认、15 候补")
    void concurrent_registrations_never_oversell() throws Exception {
        User organizer = newUser("org");
        int capacity = 5;
        int contenders = 20;
        long eventId = publishedEvent(organizer.getId(), capacity);

        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            userIds.add(newUser("contender" + i).getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<RegistrationStatus>> futures = new ArrayList<>();

        try {
            for (Long userId : userIds) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(30, TimeUnit.SECONDS);
                    return registrationService.register(userId, eventId).status();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS))
                    .as("所有线程应当就绪").isTrue();
            go.countDown();

            List<RegistrationStatus> outcomes = new ArrayList<>();
            for (Future<RegistrationStatus> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }

            assertThat(outcomes).filteredOn(s -> s == RegistrationStatus.CONFIRMED)
                    .as("确认人数必须正好等于容量").hasSize(capacity);
            assertThat(outcomes).filteredOn(s -> s == RegistrationStatus.WAITLISTED)
                    .as("其余全部进入候补").hasSize(contenders - capacity);
        } finally {
            pool.shutdownNow();
        }

        // 数据库层面的最终校验，独立于上面收集到的返回值
        assertThat(registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED))
                .isEqualTo(capacity);
        assertThat(registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.WAITLISTED))
                .isEqualTo(contenders - capacity);
    }

    @Test
    @DisplayName("未发布的活动不接受报名")
    void draft_event_rejects_registration() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createDraftEvent(organizer, 10);

        mvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_OPEN"));
    }

    @Test
    @DisplayName("我的报名列表带出活动摘要")
    void my_registrations_include_event_summary() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);
        registerForEvent(user, eventId);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/me/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].event.id").value(eventId))
                .andExpect(jsonPath("$.data.items[0].event.title").isNotEmpty());
    }

    @Test
    @DisplayName("取消报名后可以重新报名，复用同一条记录并换发票券码")
    void re_register_after_cancel_reuses_row() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);

        long registrationId = registerForEvent(user, eventId);
        MvcResult before = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/me/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andReturn();
        String oldTicket = data(before).path("items[0].ticketCode").asText();

        mvc.perform(delete("/api/v1/registrations/" + registrationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());

        MvcResult again = mvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andReturn();

        assertThat(data(again).path("id").asLong())
                .as("重新报名应复用同一行").isEqualTo(registrationId);
        assertThat(data(again).path("ticketCode").asText())
                .as("票券码应换新").isNotEqualTo(oldTicket);
    }

    // ---------- 辅助方法 ----------

    private RegistrationStatus reloadStatus(Long registrationId) {
        return registrationRepository.findById(registrationId).orElseThrow().getStatus();
    }

    private User newUser(String prefix) {
        return userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("unused-in-this-test")
                .nickname(prefix)
                .build());
    }

    private long publishedEvent(Long organizerId, int capacity) {
        Event event = Event.builder()
                .organizerId(organizerId)
                .title("并发测试-" + UUID.randomUUID().toString().substring(0, 6))
                .location("测试场地")
                .startAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .capacity(capacity)
                .build();
        event.publish();
        return eventRepository.save(event).getId();
    }
}
