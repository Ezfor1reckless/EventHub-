package com.eventhub.api.notification.service;

import com.eventhub.api.config.RabbitConfig;
import com.eventhub.api.notification.domain.Notification;
import com.eventhub.api.notification.domain.NotificationChannel;
import com.eventhub.api.notification.domain.NotificationType;
import com.eventhub.api.notification.gateway.MailGateway;
import com.eventhub.api.notification.repository.NotificationRepository;
import com.eventhub.api.outbox.OutboxEventTypes;
import com.eventhub.api.outbox.OutboxMessage;
import com.eventhub.api.outbox.OutboxPayloads;
import com.eventhub.api.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MailGateway mailGateway;
    private final ObjectMapper objectMapper;

    /**
     * 处理通知类事件。
     *
     * <p>方法抛出异常时，Spring AMQP 会按配置重试；重试用尽后消息进入死信队列。
     * 这里不做 try-catch 包住整体——真正的基础设施故障应该走重试而不是被吞掉。
     */
    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    @Transactional
    public void onNotification(OutboxMessage message) {
        switch (message.eventType()) {
            case OutboxEventTypes.REGISTRATION_CONFIRMED -> handleRegistrationConfirmed(message);
            case OutboxEventTypes.WAITLIST_PROMOTED -> handleWaitlistPromoted(message);
            case OutboxEventTypes.EVENT_CANCELLED -> handleEventCancelled(message);
            default -> log.warn("未知通知事件类型，已忽略 eventType={} outboxId={}",
                    message.eventType(), message.id());
        }
    }

    private void handleRegistrationConfirmed(OutboxMessage message) {
        OutboxPayloads.RegistrationConfirmed payload =
                payload(message, OutboxPayloads.RegistrationConfirmed.class);

        deliver(payload.userId(), NotificationType.REGISTRATION_CONFIRMED,
                "报名成功：" + payload.eventTitle(),
                "你已成功报名「%s」，票券码 %s，活动时间 %s。"
                        .formatted(payload.eventTitle(), payload.ticketCode(), payload.eventStartAt()),
                payload.eventId(), message.id());
    }

    private void handleWaitlistPromoted(OutboxMessage message) {
        OutboxPayloads.WaitlistPromoted payload =
                payload(message, OutboxPayloads.WaitlistPromoted.class);

        deliver(payload.userId(), NotificationType.WAITLIST_PROMOTED,
                "候补转正：" + payload.eventTitle(),
                "有人取消报名，「%s」的名额已递补给你，票券码 %s。"
                        .formatted(payload.eventTitle(), payload.ticketCode()),
                payload.eventId(), message.id());
    }

    private void handleEventCancelled(OutboxMessage message) {
        OutboxPayloads.EventCancelled payload =
                payload(message, OutboxPayloads.EventCancelled.class);

        for (Long userId : payload.recipientUserIds()) {
            deliver(userId, NotificationType.EVENT_CANCELLED,
                    "活动已取消：" + payload.eventTitle(),
                    "很抱歉，「%s」已被组织者取消。".formatted(payload.eventTitle()),
                    payload.eventId(), message.id());
        }
    }

    /**
     * 落一条站内信并发一封邮件。
     *
     * <p>幂等判断放在最前面：Outbox 是至少一次投递，同一条事件可能被消费两次。
     * 这里用「先查后插」，数据库上的唯一键 (source_event_id, user_id) 是并发下的兜底——
     * 真的撞上时整条消息会失败重试，而重试时这些记录已存在、会被这里的判断跳过，
     * 所以最终一定收敛。
     */
    private void deliver(Long userId, NotificationType type, String title, String content,
                         Long relatedEventId, Long sourceEventId) {

        if (notificationRepository.existsBySourceEventIdAndUserId(sourceEventId, userId)) {
            log.debug("通知已存在，跳过重复消费 outboxId={} userId={}", sourceEventId, userId);
            return;
        }

        Notification notification = new Notification(
                userId, type, title, content, NotificationChannel.IN_APP,
                "Event", String.valueOf(relatedEventId), sourceEventId);
        notificationRepository.save(notification);

        // 邮件是尽力而为：站内信已经落库，不该因为邮件服务器抽风而让整条消息重试。
        // 已知代价：SMTP 的 I/O 发生在数据库事务内，会拉长事务持有连接的时间。
        // 若将来邮件量大，应改为事务提交后再发（TransactionSynchronization）。
        userRepository.findById(userId).ifPresent(user -> {
            try {
                mailGateway.send(user.getEmail(), title, content);
                notification.markSent();
            } catch (Exception ex) {
                log.warn("邮件发送失败，站内信不受影响 userId={} 原因={}", userId, ex.getMessage());
                notification.markFailed();
            }
        });
    }

    private <T> T payload(OutboxMessage message, Class<T> type) {
        return objectMapper.convertValue(message.payload(), type);
    }
}
