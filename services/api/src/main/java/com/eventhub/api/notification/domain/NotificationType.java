package com.eventhub.api.notification.domain;

public enum NotificationType {
    /** 报名成功占到了名额。 */
    REGISTRATION_CONFIRMED,
    /** 候补转正。 */
    WAITLIST_PROMOTED,
    /** 活动被组织者取消。 */
    EVENT_CANCELLED,
    /** 活动开始前的提醒。 */
    EVENT_REMINDER
}
