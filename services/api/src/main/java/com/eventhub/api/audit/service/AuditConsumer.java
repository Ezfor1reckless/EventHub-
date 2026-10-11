package com.eventhub.api.audit.service;

import com.eventhub.api.audit.domain.AuditLog;
import com.eventhub.api.audit.repository.AuditLogRepository;
import com.eventhub.api.config.RabbitConfig;
import com.eventhub.api.outbox.OutboxEventTypes;
import com.eventhub.api.outbox.OutboxMessage;
import com.eventhub.api.outbox.OutboxPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditConsumer {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.AUDIT_QUEUE)
    @Transactional
    public void onAudit(OutboxMessage message) {
        if (!OutboxEventTypes.AUDIT_RECORDED.equals(message.eventType())) {
            // 路由到了错误的队列。记录并丢弃，不重试——重试也不会变对。
            log.warn("审计队列收到非审计事件，已忽略 eventType={} outboxId={}",
                    message.eventType(), message.id());
            return;
        }

        // 幂等：唯一键上的这层判断让重复消费变成空操作
        if (auditLogRepository.existsBySourceEventId(message.id())) {
            log.debug("审计记录已存在，跳过重复消费 outboxId={}", message.id());
            return;
        }

        OutboxPayloads.AuditRecorded payload =
                objectMapper.convertValue(message.payload(), OutboxPayloads.AuditRecorded.class);

        auditLogRepository.save(new AuditLog(
                payload.actorId(),
                payload.action(),
                payload.targetType(),
                payload.targetId(),
                payload.detail(),
                payload.ip(),
                message.id()));
    }
}
