-- 阶段 3：异步链路（Outbox）与审计、站内信。

-- Outbox 表：业务事务内只写这里，不直接发 MQ。
-- 由独立的投递器轮询并转发到 RabbitMQ，从而避免
-- 「消息已经发出但数据库事务随后回滚」导致的下游幻读。
CREATE TABLE outbox_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    -- 事件发往哪个队列。显式存下来而不是从 event_type 推导：
    -- 推导规则会在新增事件类型时静默失配（消息发到没人监听的队列，不报错也不落库）。
    routing_key    VARCHAR(64)  NOT NULL,
    -- 存 TEXT 而非 MySQL 的 JSON 类型：Hibernate 的 String 到 JSON 列映射需要
    -- 额外的 @JdbcTypeCode，而 ddl-auto=validate 对类型比对很严格。
    -- 代价是失去 JSON 校验和函数，当前没有这类查询需求。
    payload        TEXT         NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(512) NULL,
    created_at     DATETIME(6)  NOT NULL,
    published_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- 投递器的扫描条件是「status = PENDING 且按创建时间排序」，这个索引正好覆盖
    KEY idx_outbox_status_created (status, created_at),
    KEY idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE notifications (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id      BIGINT        NOT NULL,
    type         VARCHAR(48)   NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    content      VARCHAR(2000) NOT NULL,
    channel      VARCHAR(16)   NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    related_type VARCHAR(64)   NULL,
    related_id   VARCHAR(64)   NULL,
    -- 来源 Outbox 事件 id，用于消费幂等。可空：非事件驱动的通知填 NULL，
    -- MySQL 的唯一索引允许多个 NULL，所以它们不会互相冲突。
    source_event_id BIGINT     NULL,
    read_at      DATETIME(6)   NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user_created (user_id, created_at),
    KEY idx_notifications_unread (user_id, read_at),
    -- 幂等键是 (事件, 用户) 而不是单独的事件：一条「活动取消」事件会给
    -- 该活动所有已确认用户各生成一条通知，用事件 id 单独做唯一键会把它们全挡掉。
    CONSTRAINT uk_notifications_source_user UNIQUE (source_event_id, user_id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 审计日志刻意不建到 users 的外键：用户被删除后，审计记录必须留存。
-- actor_id 是普通列，可能指向一个已不存在的用户，这是预期的。
CREATE TABLE audit_logs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT      NULL,
    action      VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id   VARCHAR(64) NULL,
    detail      TEXT        NULL,
    ip          VARCHAR(64) NULL,
    -- 来源 Outbox 事件 id，消费幂等用。审计事件与审计记录是一对一，所以单列唯一即可。
    source_event_id BIGINT  NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_audit_logs_source_event UNIQUE (source_event_id),
    KEY idx_audit_logs_actor (actor_id),
    KEY idx_audit_logs_created (created_at),
    KEY idx_audit_logs_action (action)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
