package com.eventhub.api.user.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名。存原样，查询时忽略大小写。 */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 32)
    private String phone;

    /** BCrypt 摘要，绝不存明文。 */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String nickname;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * 角色集合。EAGER 是刻意的：认证路径每次都要读角色，
     * 且集合很小（≤3 个枚举），不值得为它引入一次额外查询或二级缓存。
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public User(String email, String phone, String passwordHash, String nickname,
                String avatarUrl, UserStatus status, Set<Role> roles) {
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.status = status == null ? UserStatus.ACTIVE : status;
        this.roles = roles == null || roles.isEmpty()
                ? EnumSet.of(Role.USER)
                : EnumSet.copyOf(roles);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }
}
