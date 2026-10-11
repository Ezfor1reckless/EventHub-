package com.eventhub.api.user.domain;

/**
 * 账号状态。只有 {@link #ACTIVE} 允许登录。
 */
public enum UserStatus {
    ACTIVE,
    /** 邮箱未验证，暂不允许登录。 */
    PENDING,
    /** 被管理员禁用。 */
    DISABLED
}
