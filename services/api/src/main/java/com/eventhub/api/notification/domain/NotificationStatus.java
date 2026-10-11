package com.eventhub.api.notification.domain;

/**
 * 投递状态，与「是否已读」无关——已读由 {@code readAt} 表示。
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
