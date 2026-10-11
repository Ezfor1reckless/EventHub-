package com.eventhub.api.registration.domain;

/**
 * 报名状态机（策划原文）：
 *
 * <pre>
 *   PENDING ──▶ CONFIRMED ──▶ CHECKED_IN
 *      │            │
 *      └──▶ WAITLISTED ──(有人取消，递补)──▶ CONFIRMED
 *      │            │
 *      └────────────┴──▶ CANCELLED
 * </pre>
 *
 * <p>阶段 2 不产生 {@link #PENDING}——没有支付环节，报名即确定名额或直接进候补。
 * 保留它是为了将来接支付/审核时不必改状态机。
 */
public enum RegistrationStatus {
    PENDING,
    CONFIRMED,
    WAITLISTED,
    CANCELLED,
    CHECKED_IN;

    /** 是否是一条「还在生效」的报名。取消后重新报名会复用同一行，所以这个判断很常用。 */
    public boolean isActive() {
        return this != CANCELLED;
    }

    /** 是否占用名额。候补和已取消都不占。 */
    public boolean occupiesSeat() {
        return this == CONFIRMED || this == CHECKED_IN;
    }

    public boolean isOnWaitlist() {
        return this == WAITLISTED;
    }
}
