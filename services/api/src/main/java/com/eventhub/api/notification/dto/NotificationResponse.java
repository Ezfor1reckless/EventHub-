package com.eventhub.api.notification.dto;

import com.eventhub.api.notification.domain.Notification;
import com.eventhub.api.notification.domain.NotificationStatus;
import com.eventhub.api.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "站内信")
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        NotificationStatus status,
        @Schema(description = "关联对象类型，如 Event") String relatedType,
        @Schema(description = "关联对象 id，前端据此跳转") String relatedId,
        @Schema(description = "已读时刻，null 表示未读") Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getStatus(),
                notification.getRelatedType(),
                notification.getRelatedId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
