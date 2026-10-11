package com.eventhub.api.outbox;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 事件的负载定义。
 *
 * <p>每个负载都携带渲染一条通知所需的<b>全部</b>信息（活动标题、时间等），
 * 消费端不必回头查库。这是刻意的取舍：负载会冗余一些，但消费端与业务表解耦，
 * 重放历史事件时也不会读到「现在」的数据而得到错误结果。
 */
public final class OutboxPayloads {

    private OutboxPayloads() {
    }

    public record RegistrationConfirmed(
            Long registrationId,
            Long eventId,
            Long userId,
            String ticketCode,
            String eventTitle,
            Instant eventStartAt
    ) {
    }

    public record WaitlistPromoted(
            Long registrationId,
            Long eventId,
            Long userId,
            String ticketCode,
            String eventTitle,
            Instant eventStartAt
    ) {
    }

    /**
     * @param recipientUserIds 需要收到通知的用户 id。刻意把名单快照进负载，
     *                         而不是让消费端回查报名表：消费发生在将来某一刻，
     *                         那时报名表可能已被改动，重放历史事件会得到错误结果。
     *                         代价是负载随活动规模增长——万人活动不适合这种设计，
     *                         届时应改为分片投递。
     */
    public record EventCancelled(
            Long eventId,
            Long organizerId,
            String eventTitle,
            List<Long> recipientUserIds
    ) {
    }

    public record AuditRecorded(
            Long actorId,
            String action,
            String targetType,
            String targetId,
            String detail,
            String ip
    ) {
    }
}
