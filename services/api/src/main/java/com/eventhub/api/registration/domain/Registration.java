package com.eventhub.api.registration.domain;

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
 * 一条报名记录。唯一键落在 {@code (event_id, user_id)} 上——一个用户在一个活动里
 * 永远只有这一行，「取消后重新报名」是把它重新激活，而不是插一行新的。
 */
@Entity
@Table(name = "registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RegistrationStatus status;

    /** 电子票券编号，用于人工核验与展示。全局唯一。 */
    @Column(name = "ticket_code", nullable = false, length = 32)
    private String ticketCode;

    /** 进入候补的时刻。候补排名按它排，不按 id（重新报名会复用旧行）。 */
    @Column(name = "waitlisted_at")
    private Instant waitlistedAt;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public Registration(Long eventId, Long userId, String ticketCode) {
        this.eventId = eventId;
        this.userId = userId;
        this.ticketCode = ticketCode;
        this.status = RegistrationStatus.PENDING;
        this.registeredAt = Instant.now();
    }

    // ---------- 状态流转 ----------

    /** 确认名额。会清掉候补时间戳，避免重新入候补时排名用到旧值。 */
    public void confirm() {
        this.status = RegistrationStatus.CONFIRMED;
        this.waitlistedAt = null;
        this.cancelledAt = null;
    }

    public void waitlist() {
        this.status = RegistrationStatus.WAITLISTED;
        // 只在首次进入候补时打时间戳；已在候补队列里就不重置，防止反复刷新把自己排到队尾
        if (this.waitlistedAt == null) {
            this.waitlistedAt = Instant.now();
        }
        this.cancelledAt = null;
    }

    public void cancel() {
        this.status = RegistrationStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.waitlistedAt = null;
    }

    public void checkIn() {
        this.status = RegistrationStatus.CHECKED_IN;
    }

    /** 取消后重新报名：票券码换新，旧的作废。 */
    public void reactivate(String newTicketCode) {
        this.ticketCode = newTicketCode;
        this.registeredAt = Instant.now();
        this.cancelledAt = null;
        this.waitlistedAt = null;
    }
}
