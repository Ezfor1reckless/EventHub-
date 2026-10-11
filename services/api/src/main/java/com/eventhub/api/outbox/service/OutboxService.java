package com.eventhub.api.outbox.service;

import com.eventhub.api.outbox.domain.OutboxEvent;
import com.eventhub.api.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 往 Outbox 表写事件。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>：它必须加入调用方已有的事务，
 * 才能与业务改动同生共死。加上默认的 REQUIRED 传播也是这个效果，但不加注解
 * 更明确地表达「我不是事务边界，别把我当事务边界」。
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 记录一条待投递事件。与调用方的业务变更在同一个事务里提交或回滚。
     *
     * @param aggregateType 聚合类型，用 {@code OutboxEventTypes.AGGREGATE_*} 常量
     * @param aggregateId   聚合 id
     * @param eventType     事件类型，用 {@code OutboxEventTypes} 里的常量
     * @param routingKey    投递目标队列，用 {@code OutboxEventTypes.ROUTING_*} 常量
     * @param payload       负载对象，会被序列化成 JSON
     */
    public void enqueue(String aggregateType, Object aggregateId, String eventType,
                        String routingKey, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            // 序列化失败说明负载类型写错了，是编码错误而非运行时故障，直接失败更好
            throw new IllegalStateException("Outbox 事件负载无法序列化: " + eventType, ex);
        }
        outboxEventRepository.save(new OutboxEvent(
                aggregateType, String.valueOf(aggregateId), eventType, routingKey, json));
    }
}
