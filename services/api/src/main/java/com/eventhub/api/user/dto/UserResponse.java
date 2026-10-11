package com.eventhub.api.user.dto;

import com.eventhub.api.user.domain.Role;
import com.eventhub.api.user.domain.User;
import com.eventhub.api.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;

/**
 * 对外的用户视图。刻意不含 passwordHash——不要用实体直接做响应体。
 */
@Schema(description = "用户信息")
public record UserResponse(
        Long id,
        String email,
        String nickname,
        String avatarUrl,
        String phone,
        Set<Role> roles,
        UserStatus status,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getPhone(),
                Set.copyOf(user.getRoles()),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
