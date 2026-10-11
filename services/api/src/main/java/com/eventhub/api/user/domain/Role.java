package com.eventhub.api.user.domain;

/**
 * 系统角色。Spring Security 的权限串统一加 {@code ROLE_} 前缀。
 */
public enum Role {
    USER,
    ORGANIZER,
    ADMIN;

    /** 转成 Spring Security 认识的 authority 形式。 */
    public String authority() {
        return "ROLE_" + name();
    }
}
