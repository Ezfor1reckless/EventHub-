package com.eventhub.api.outbox;

import com.eventhub.api.audit.repository.AuditLogRepository;
import com.eventhub.api.notification.repository.NotificationRepository;
import com.eventhub.api.notification.service.NotificationConsumer;
import com.eventhub.api.outbox.domain.OutboxEvent;
import com.eventhub.api.outbox.domain.OutboxStatus;
import com.eventhub.api.outbox.repository.OutboxEventRepository;
import com.eventhub.api.outbox.service.OutboxPublisher;
import com.eventhub.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox 异步链路测试：业务写入 → 投递器 → RabbitMQ → 消费者 → 落库。
 *
 * <p>投递器在测试配置里被设为 1 小时间隔（只在启动时跑一次），所以这里显式调用
 * {@code publishPending()}，让时序可控。
 */
class OutboxFlowIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private NotificationConsumer notificationConsumer;

    @Test
    @DisplayName("报名成功时，事件与业务变更在同一事务里写入 Outbox")
    void registration_writes_an_outbox_event() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);
        long userId = currentUserId(user);

        registerForEvent(user, eventId);

        List<OutboxEvent> events = registrationEventsFor(userId);

        assertThat(events).as("应当恰好写出一条报名确认事件").hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo(OutboxEventTypes.REGISTRATION_CONFIRMED);
        assertThat(event.getAggregateType()).isEqualTo(OutboxEventTypes.AGGREGATE_REGISTRATION);
        assertThat(event.getRoutingKey()).isEqualTo(OutboxEventTypes.ROUTING_NOTIFICATION);
        // 投递器在测试期间不会自动跑，所以事件应当还停在 PENDING
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    @DisplayName("端到端：报名后用户最终收到站内信，事件转为已投递")
    void notification_reaches_the_user() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);
        long userId = currentUserId(user);

        registerForEvent(user, eventId);

        // 手动触发投递（正常情况下由 @Scheduled 完成）
        outboxPublisher.publishPending();

        awaitUntil("用户收到报名成功通知", () ->
                notificationRepository.countByUserIdAndReadAtIsNull(userId) > 0);

        awaitUntil("Outbox 事件被标记为已投递", () ->
                registrationEventsFor(userId).stream()
                        .anyMatch(e -> e.getStatus() == OutboxStatus.PUBLISHED));
    }

    @Test
    @DisplayName("审计事件异步落到 audit_logs")
    void audit_events_reach_the_audit_log() throws Exception {
        String user = signUp("auditor");
        long userId = currentUserId(user);

        outboxPublisher.publishPending();

        awaitUntil("注册审计记录落库", () -> auditLogRepository
                .findByActorIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().stream()
                .anyMatch(log -> OutboxEventTypes.AUDIT_USER_REGISTERED.equals(log.getAction())));
    }

    @Test
    @DisplayName("候补递补会发出自己的事件")
    void waitlist_promotion_emits_its_own_event() throws Exception {
        String organizer = signUp("organizer");
        String first = signUp("first");
        String second = signUp("second");
        long eventId = createPublishedEvent(organizer, 1);

        long firstUserId = currentUserId(first);
        long secondUserId = currentUserId(second);

        long firstRegistration = registerForEvent(first, eventId);
        registerForEvent(second, eventId);

        // first 取消，second 递补
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/registrations/" + firstRegistration)
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        assertThat(eventsOfTypeFor(secondUserId, OutboxEventTypes.WAITLIST_PROMOTED))
                .as("递补应当产生一条独立事件").hasSize(1);
    }

    @Test
    @DisplayName("重复消费同一条事件不会产生第二条通知（至少一次投递下的幂等）")
    void consuming_the_same_event_twice_is_idempotent() throws Exception {
        String organizer = signUp("organizer");
        String user = signUp("alice");
        long eventId = createPublishedEvent(organizer, 10);
        long userId = currentUserId(user);
        long registrationId = registerForEvent(user, eventId);

        // 造一条消息 id 唯一的事件，避免与其它测试产生的记录混淆
        long syntheticId = 9_000_000_000L + userId;
        OutboxMessage message = new OutboxMessage(
                syntheticId,
                OutboxEventTypes.REGISTRATION_CONFIRMED,
                OutboxEventTypes.AGGREGATE_REGISTRATION,
                String.valueOf(registrationId),
                json.valueToTree(new OutboxPayloads.RegistrationConfirmed(
                        registrationId, eventId, userId, "TESTTICKET01", "幂等测试活动", Instant.now())),
                Instant.now());

        notificationConsumer.onNotification(message);
        // 模拟 At-Least-Once 语义下的重复投递
        notificationConsumer.onNotification(message);

        long count = notificationRepository.findAll().stream()
                .filter(n -> message.id().equals(n.getSourceEventId()))
                .count();

        assertThat(count).as("同一事件重复消费只应产生一条通知").isEqualTo(1);
    }

    // ---------- 辅助方法 ----------

    private List<OutboxEvent> registrationEventsFor(long userId) {
        return eventsOfTypeFor(userId, OutboxEventTypes.REGISTRATION_CONFIRMED);
    }

    private List<OutboxEvent> eventsOfTypeFor(long userId, String eventType) {
        String marker = "\"userId\":" + userId;
        return outboxEventRepository.findAll().stream()
                .filter(e -> eventType.equals(e.getEventType()))
                .filter(e -> e.getPayload().replace(" ", "").contains(marker))
                .toList();
    }
}
