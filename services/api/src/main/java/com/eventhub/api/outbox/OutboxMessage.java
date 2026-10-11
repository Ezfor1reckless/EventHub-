package com.eventhub.api.outbox;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 投递到 RabbitMQ 的消息信封。
 *
 * <p>{@code id} 就是 outbox_events 的主键，同时作为消费端的<b>幂等键</b>。
 * Outbox 模式保证的是「至少一次」而非「恰好一次」——投递成功后、事务提交前
 * 进程崩溃，恢复后这条事件会被再发一遍。消费端必须用这个 id 去重。
 */
public record OutboxMessage(
        Long id,
        String eventType,
        String aggregateType,
        String aggregateId,
        JsonNode payload,
        Instant occurredAt
) {
}
