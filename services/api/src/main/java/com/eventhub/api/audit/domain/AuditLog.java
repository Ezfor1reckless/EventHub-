package com.eventhub.api.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 审计日志。
 *
 * <p>{@code actorId} 故意不建外键：用户被删除后审计记录必须留存，
 * 因此它可能指向一个已不存在的用户。
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(length = 64)
    private String ip;

    /** 来源 Outbox 事件 id，消费幂等用（唯一）。 */
    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLog(Long actorId, String action, String targetType, String targetId,
                    String detail, String ip, Long sourceEventId) {
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.ip = ip;
        this.sourceEventId = sourceEventId;
        this.createdAt = Instant.now();
    }
}
