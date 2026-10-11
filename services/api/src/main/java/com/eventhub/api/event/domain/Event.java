package com.eventhub.api.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_url", length = 512)
    private String coverUrl;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    /** 报名上限。判定一律以数据库为准，Redis 计数只做加速。 */
    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EventStatus status = EventStatus.DRAFT;

    /**
     * 乐观锁。用途是组织者并发编辑活动（两个人同时改标题/容量），
     * <b>不</b>用于名额争夺——那条路径走悲观锁，见
     * {@code EventRepository#findByIdForUpdate}。
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public Event(Long organizerId, String title, String description, String coverUrl,
                 String location, Instant startAt, Instant endAt, int capacity) {
        this.organizerId = organizerId;
        this.title = title;
        this.description = description;
        this.coverUrl = coverUrl;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.capacity = capacity;
        this.status = EventStatus.DRAFT;
    }

    // ---------- 状态流转 ----------

    public boolean isOwnedBy(Long userId) {
        return organizerId.equals(userId);
    }

    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new IllegalStateException("只有草稿状态的活动可以发布，当前为 " + status);
        }
        this.status = EventStatus.PUBLISHED;
    }

    public void cancel() {
        if (status == EventStatus.FINISHED) {
            throw new IllegalStateException("已结束的活动不能取消");
        }
        this.status = EventStatus.CANCELLED;
    }

    public void finish() {
        if (status == EventStatus.PUBLISHED) {
            this.status = EventStatus.FINISHED;
        }
    }

    /** 组织的可编辑字段。状态与 organizerId 不在此列，各有专门的流转方法。 */
    public void updateDetails(String title, String description, String coverUrl,
                              String location, Instant startAt, Instant endAt, int capacity) {
        this.title = title;
        this.description = description;
        this.coverUrl = coverUrl;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.capacity = capacity;
    }
}
