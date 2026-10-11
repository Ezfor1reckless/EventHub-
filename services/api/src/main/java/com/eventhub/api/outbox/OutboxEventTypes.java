package com.eventhub.api.outbox;

import com.eventhub.api.config.RabbitConfig;

/**
 * 领域事件的类型常量与聚合类型常量。
 * 消费端按 {@code eventType} 分派，所以这些字符串是跨服务的契约，不要随意改名。
 */
public final class OutboxEventTypes {

    private OutboxEventTypes() {
    }

    // ---- 聚合类型 ----
    public static final String AGGREGATE_REGISTRATION = "Registration";
    public static final String AGGREGATE_EVENT = "Event";
    public static final String AGGREGATE_USER = "User";
    public static final String AGGREGATE_CHECKIN = "CheckIn";

    // ---- 事件类型 ----
    /** 报名成功占到了名额。 */
    public static final String REGISTRATION_CONFIRMED = "registration.confirmed";
    /** 候补递补成功。 */
    public static final String WAITLIST_PROMOTED = "registration.waitlist_promoted";
    /** 活动被取消。 */
    public static final String EVENT_CANCELLED = "event.cancelled";
    /** 一条审计记录。 */
    public static final String AUDIT_RECORDED = "audit.recorded";

    // ---- 路由键 ----
    // 直接复用 RabbitConfig 的常量，避免两处各写一份字符串后悄悄失配。
    public static final String ROUTING_NOTIFICATION = RabbitConfig.ROUTING_NOTIFICATION;
    public static final String ROUTING_AUDIT = RabbitConfig.ROUTING_AUDIT;
    public static final String ROUTING_ANALYTICS = RabbitConfig.ROUTING_ANALYTICS;

    // ---- 审计动作 ----
    public static final String AUDIT_USER_REGISTERED = "user.registered";
    public static final String AUDIT_USER_LOGGED_IN = "user.logged_in";
    public static final String AUDIT_EVENT_PUBLISHED = "event.published";
    public static final String AUDIT_EVENT_CANCELLED = "event.cancelled";
    public static final String AUDIT_CHECK_IN = "checkin.performed";
}

