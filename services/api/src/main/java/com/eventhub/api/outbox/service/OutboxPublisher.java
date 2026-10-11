package com.eventhub.api.outbox.service;

import com.eventhub.api.config.RabbitConfig;
import com.eventhub.api.outbox.OutboxMessage;
import com.eventhub.api.outbox.domain.OutboxEvent;
import com.eventhub.api.outbox.domain.OutboxStatus;
import com.eventhub.api.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 投递器：轮询 outbox_events 表，把 PENDING 的事件转发到 RabbitMQ。
 *
 * <p>投递语义是<b>至少一次</b>，不是恰好一次。发完消息、事务还没提交时进程崩溃，
 * 恢复后这条事件会被重新投递。这不是缺陷而是取舍——要做到恰好一次需要分布式事务，
 * 代价远大于收益。消费端用消息里的 {@code id} 做幂等即可。
 *
 * <p>失败的事件不回滚整个批次，只累加它自己的 attempts；达到上限后转 FAILED
 * 不再投递，等待人工介入。无休止重试一条永远失败的消息只会淹没日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.publisher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval:2s}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.findBatchForPublishing(
                OutboxStatus.PENDING, PageRequest.of(0, batchSize()));
        if (batch.isEmpty()) {
            return;
        }

        int published = 0;
        for (OutboxEvent event : batch) {
            if (tryPublish(event)) {
                published++;
            }
        }

        if (published > 0) {
            log.debug("Outbox 投递完成：{}/{} 条", published, batch.size());
        }
    }

    private boolean tryPublish(OutboxEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EVENTS_EXCHANGE,
                    event.getRoutingKey(),
                    buildMessage(event),
                    message -> {
                        // 持久化：broker 重启后消息不丢
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        // 幂等键，消费端据此去重
                        message.getMessageProperties().setHeader("outboxId", event.getId());
                        message.getMessageProperties().setHeader("eventType", event.getEventType());
                        return message;
                    });

            event.markPublished();
            return true;

        } catch (Exception ex) {
            // 单条失败不影响同批次其它事件。
            // RabbitTemplate 抛的是 AmqpException，不会把当前事务标记为 rollback-only，
            // 所以这里的 catch 不会导致后面的提交失败。
            event.markFailed(ex.getMessage());
            log.warn("Outbox 投递失败 id={} eventType={} attempts={} 原因={}",
                    event.getId(), event.getEventType(), event.getAttempts(), ex.getMessage());
            return false;
        }
    }

    private OutboxMessage buildMessage(OutboxEvent event) throws Exception {
        return new OutboxMessage(
                event.getId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                objectMapper.readTree(event.getPayload()),
                event.getCreatedAt());
    }

    private int batchSize() {
        return 100;
    }
}
