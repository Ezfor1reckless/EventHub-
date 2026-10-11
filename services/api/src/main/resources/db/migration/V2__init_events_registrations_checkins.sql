-- 阶段 2：活动、报名、签到。
-- notifications / outbox_events / audit_logs 属于阶段 3 的异步链路，放 V3。

CREATE TABLE events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    organizer_id BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT         NULL,
    cover_url    VARCHAR(512) NULL,
    location     VARCHAR(255) NOT NULL,
    start_at     DATETIME(6)  NOT NULL,
    end_at       DATETIME(6)  NOT NULL,
    capacity     INT          NOT NULL,
    status       VARCHAR(24)  NOT NULL,
    -- 组织者并发编辑活动的乐观锁
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_events_status_start (status, start_at),
    KEY idx_events_organizer (organizer_id),
    CONSTRAINT fk_events_organizer
        FOREIGN KEY (organizer_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 唯一键建在 (event_id, user_id) 上：一个用户在一个活动里永远只有一行。
-- 「取消后重新报名」复用同一行改状态，而不是插新行——这样唯一约束本身就是
-- 并发的报名幂等保证，不必依赖应用层的先查后插。
CREATE TABLE registrations (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    event_id      BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    status        VARCHAR(24) NOT NULL,
    ticket_code   VARCHAR(32) NOT NULL,
    -- 进入候补的时刻。候补排名按它排，不能按 id：
    -- 取消后重新报名会复用旧行，按 id 排会凭空插队。
    waitlisted_at DATETIME(6) NULL,
    registered_at DATETIME(6) NOT NULL,
    cancelled_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_registrations_event_user UNIQUE (event_id, user_id),
    CONSTRAINT uk_registrations_ticket_code UNIQUE (ticket_code),
    KEY idx_registrations_event_status (event_id, status),
    KEY idx_registrations_user (user_id),
    CONSTRAINT fk_registrations_event
        FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_registrations_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 一条报名最多一条签到记录。这个唯一键是「重复签到被拒绝」的最后一道防线：
-- 即使应用层判断出错，数据库也不会接受第二条。
CREATE TABLE check_ins (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    registration_id BIGINT      NOT NULL,
    event_id        BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    checked_in_at   DATETIME(6) NOT NULL,
    method          VARCHAR(24) NOT NULL,
    operator_id     BIGINT      NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_check_ins_registration UNIQUE (registration_id),
    KEY idx_check_ins_event (event_id),
    CONSTRAINT fk_check_ins_registration
        FOREIGN KEY (registration_id) REFERENCES registrations (id) ON DELETE CASCADE,
    CONSTRAINT fk_check_ins_event
        FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_check_ins_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_check_ins_operator
        FOREIGN KEY (operator_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
