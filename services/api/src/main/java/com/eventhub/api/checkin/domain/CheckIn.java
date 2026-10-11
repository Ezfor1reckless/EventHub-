package com.eventhub.api.checkin.domain;

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
 * 签到记录。表中对 {@code registration_id} 建了唯一索引，
 * 所以「同一条报名不能签到两次」由数据库兜底，不只靠应用层判断。
 */
@Entity
@Table(name = "check_ins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CheckInMethod method;

    /** 手工补签时的操作者。自助扫码签到为 null。 */
    @Column(name = "operator_id")
    private Long operatorId;

    public CheckIn(Long registrationId, Long eventId, Long userId, CheckInMethod method, Long operatorId) {
        this.registrationId = registrationId;
        this.eventId = eventId;
        this.userId = userId;
        this.method = method;
        this.operatorId = operatorId;
        this.checkedInAt = Instant.now();
    }
}
