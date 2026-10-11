package com.eventhub.api.outbox.domain;

public enum OutboxStatus {
    /** 待投递。投递器会扫描这个状态。 */
    PENDING,
    /** 已成功投递到 RabbitMQ。 */
    PUBLISHED,
    /** 重试次数用尽，不再投递。需要人工介入。 */
    FAILED
}
