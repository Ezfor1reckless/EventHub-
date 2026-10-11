package com.eventhub.api.event.domain;

/**
 * 活动状态机：
 *
 * <pre>
 *   DRAFT ──publish──▶ PUBLISHED ──(endAt 已过)──▶ FINISHED
 *     │                    │
 *     └────cancel──────────┴──────▶ CANCELLED
 * </pre>
 *
 * <p>只有 {@link #PUBLISHED} 允许报名。
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    FINISHED;

    public boolean isRegistrationOpen() {
        return this == PUBLISHED;
    }
}
