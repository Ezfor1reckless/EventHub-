package com.eventhub.api.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 待投递的领域事件。
 *
 * <p>它是 Outbox 模式的核心：业务代码在<b>自己的事务里</b>往这张表插一行，
 * 而不是直接往 RabbitMQ 发消息。原因很直白——发 MQ 和提交数据库无法组成一个原子操作：
 * 先发消息再提交，事务回滚后下游就收到了一条根本不存在的变更；先提交再发消息，
 * 进程在两者之间崩溃就永久丢消息。写进同一张表则两者同生共死。
 *
 * <p>代价是事件变成最终一致而非实时，且多了一次轮询。对本项目完全可以接受。
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    /** 投递失败重试上限，超过则标记 FAILED 等待人工处理。 */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 聚合类型，如 "Registration" / "Event"。 */
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    /** 事件类型，如 "registration.confirmed"。消费端据此分派。 */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /**
     * 投递到哪个队列。显式存储而非从 {@code eventType} 推导——推导规则一旦
     * 与新事件类型不匹配，消息会被发到一个没人监听的 routing key 上，
     * 既不报错也不落库，是最难排查的一类故障。
     */
    @Column(name = "routing_key", nullable = false, length = 64)
    private String routingKey;

    /** 事件负载，JSON 字符串。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEvent(String aggregateType, String aggregateId, String eventType,
                       String routingKey, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    /**
     * 记录一次投递失败。达到上限后转为 FAILED，不再被投递器扫描——
     * 无休止重试一条永远失败的消息只会淹没日志，不如让它停下来等人看。
     */
    public void markFailed(String error) {
        this.attempts++;
        this.lastError = truncate(error);
        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = OutboxStatus.FAILED;
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
