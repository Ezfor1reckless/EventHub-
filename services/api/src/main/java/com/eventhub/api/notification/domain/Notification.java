package com.eventhub.api.notification.domain;

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

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    /** 关联对象，便于前端跳转，例如 ("Event", 42)。 */
    @Column(name = "related_type", length = 64)
    private String relatedType;

    @Column(name = "related_id", length = 64)
    private String relatedId;

    /**
     * 来源 Outbox 事件 id，消费幂等用。唯一键是 (source_event_id, user_id)，
     * 所以重复消费同一事件时第二条插入会被数据库拒绝。
     */
    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Notification(Long userId, NotificationType type, String title, String content,
                        NotificationChannel channel, String relatedType, String relatedId,
                        Long sourceEventId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.channel = channel;
        this.status = NotificationStatus.PENDING;
        this.relatedType = relatedType;
        this.relatedId = relatedId;
        this.sourceEventId = sourceEventId;
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    public void markRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }
}
