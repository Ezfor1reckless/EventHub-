package com.eventhub.api.security;

import com.eventhub.api.user.domain.Role;

import java.util.Set;

/**
 * 放进 SecurityContext 的认证主体。只带认证与鉴权需要的最小信息，
 * 不持有 JPA 实体，避免游离态实体被带出事务边界。
 */
public record AuthPrincipal(Long userId, String email, Set<Role> roles) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
