# 数据库设计

MySQL 8.4，字符集 `utf8mb4` / `utf8mb4_unicode_ci`。所有时间列都是 `DATETIME(6)`，
以 UTC 存储（`spring.jpa.properties.hibernate.jdbc.time_zone=UTC`）。

## 迁移

结构由 Flyway 掌管，**禁止手工改表**。`spring.jpa.hibernate.ddl-auto=validate`，
实体与迁移脚本不一致时应用启动即失败——这是刻意的，让漂移在最早的时刻暴露。

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `V1__init_identity.sql` | `users`、`user_roles`、`refresh_tokens` |
| V2 | `V2__init_events_registrations_checkins.sql` | `events`、`registrations`、`check_ins` |
| V3（待做） | — | `notifications`、`outbox_events`、`audit_logs` |

## 表

### users

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AI | |
| email | VARCHAR(255) UK | 登录名 |
| phone | VARCHAR(32) | 可空 |
| password_hash | VARCHAR(100) | BCrypt 摘要 |
| nickname | VARCHAR(64) | |
| avatar_url | VARCHAR(512) | |
| status | VARCHAR(16) | ACTIVE / PENDING / DISABLED |
| created_at, updated_at | DATETIME(6) | 由 JPA Auditing 维护 |

唯一键建在 `email` 上，**区分大小写**。大小写不敏感由应用层的
`findByEmailIgnoreCase` / `existsByEmailIgnoreCase` 保证。这样做的代价是
偶尔会走不到索引；若将来注册量上来，应改为 `email` 列用 `utf8mb4_0900_ai_ci`
排序规则或额外存一列小写邮箱。

### user_roles

`(user_id, role)` 联合主键，`role` 为枚举字符串。用 `@ElementCollection` 映射——
角色是用户的附属属性，没有独立生命周期，不值得为它建一张实体表。

### refresh_tokens

| 列 | 说明 |
|---|---|
| token_hash | CHAR(64)，SHA-256 十六进制，**唯一** |
| expires_at | 过期时刻 |
| revoked | BIT(1) |

**只存摘要**。明文刷新令牌只在签发那一刻返回客户端一次，服务端不留。
这样即使数据库被拖库，攻击者也无法用它换取访问令牌。

`revoked` 用 `BIT(1)` 而非 `TINYINT`，这是 Hibernate 6 对 MySQL 布尔值的默认映射，
写成 `TINYINT(1)` 会导致 `ddl-auto=validate` 启动失败。

### events

| 列 | 说明 |
|---|---|
| organizer_id | FK → users |
| capacity | 报名上限 |
| status | DRAFT / PUBLISHED / CANCELLED / FINISHED |
| version | 乐观锁，供组织者并发编辑 |

`idx_events_status_start` 服务于「列出已发布活动并按开始时间排序」这个主查询。

### registrations

**唯一键 `(event_id, user_id)`** —— 一个用户在一个活动里永远只有一行。
「取消后重新报名」复用同一行改状态，而不是插新行。这个设计带来两个好处：

1. 并发的重复报名由数据库唯一约束直接拦下，不需要应用层「先查后插」（那有竞态）；
2. 报名的幂等性成为结构性保证，而不是靠代码自觉。

| 列 | 说明 |
|---|---|
| status | PENDING / CONFIRMED / WAITLISTED / CANCELLED / CHECKED_IN |
| ticket_code | VARCHAR(32) 唯一，Crockford Base32（去掉易混的 I/L/O/U） |
| waitlisted_at | 进入候补的时刻，**候补排名的排序键** |
| registered_at, cancelled_at | |

`waitlisted_at` 而不是主键 id 作为候补排序键，是因为重新报名会复用旧行——
用自增 id 排序会让一个早就取消过的用户凭空插到队首。

### check_ins

**唯一键 `registration_id`** —— 一条报名最多一条签到记录。

这是「重复签到被拒绝」的最后一道防线。应用层在插入前会先查一次
`existsByRegistrationId`，但那个判断在并发下不可靠（两个请求可能同时通过检查），
唯一索引才是真正兜底的那一层。服务层用 `saveAndFlush` 让冲突在此刻而非提交时抛出。

## 一致性与并发

### 乐观锁 vs 悲观锁

项目里两种锁都在用，用途严格区分：

| 场景 | 机制 | 位置 |
|---|---|---|
| 组织者并发编辑活动 | 乐观锁 `@Version` | `events.version` |
| 名额争夺（报名/取消/递补） | 悲观写锁 `SELECT … FOR UPDATE` | `EventRepository.findByIdForUpdate` |

名额争夺选悲观锁，是因为「数当前占位人数 → 决定确认还是候补」是一个读-判断-写序列。
乐观锁会让并发中的一方失败并需要重试，重试逻辑得自己写且容易漏；悲观锁让后来者
排队等待，代码是直的。锁粒度是单个活动行，不同活动之间不互相阻塞。

### 加锁顺序（重要）

**全项目统一：先锁 `events` 行，再锁 `registrations` 行。**

`register()` 和 `cancel()` 都必须遵守。`cancel()` 里"先无锁读一次拿到 eventId、
再按顺序加锁、拿到锁后重新读一遍"的写法看起来绕，正是为了维持这个顺序——
如果它图省事先锁报名再锁活动，两个并发事务就会各持一把锁互相等待，直接死锁。

### 外键与外键删除

`registrations` / `check_ins` 对 `events` 和 `users` 都是 `ON DELETE CASCADE`：
活动删了，它的报名和签到记录没有独立意义。

## 尚未建立的部分

- `notifications`、`outbox_events`、`audit_logs`（阶段 3）
- 报名人数计数的 Redis 缓存（阶段 3；当前一律查库，容量判定以库为准）
- 候补队列位置的实时计算（当前不返回 queuePosition）
