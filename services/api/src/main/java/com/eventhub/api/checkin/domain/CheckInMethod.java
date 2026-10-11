package com.eventhub.api.checkin.domain;

public enum CheckInMethod {
    /** 用户扫码自助签到。 */
    QR_SCAN,
    /** 组织者在后台手工补签。 */
    MANUAL
}
