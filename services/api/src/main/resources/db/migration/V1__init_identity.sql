-- 阶段 1：身份与认证。
-- 业务表（events / registrations / checkins / notifications / outbox / audit_logs）
-- 留到阶段 2、3 的迁移，保持每个迁移的职责单一。

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(32)  NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64)  NOT NULL,
    avatar_url    VARCHAR(512) NULL,
    status        VARCHAR(16)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 只存 SHA-256 摘要，明文刷新令牌仅在签发时返回客户端一次。
CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked    BIT(1)      NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
