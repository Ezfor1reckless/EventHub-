# 架构说明

## 1. 分层与请求链路

```text
浏览器 / Expo App
   │  HTTPS
   ▼
Nginx ──/──────────▶ Next.js (apps/web, :3000)
   ├───/api────────▶ Spring Boot (services/api, :8080)
   ├───/matomo─────▶ Matomo (infra, :80)
   └───/.well-known/acme-challenge──▶ Certbot webroot
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
     MySQL 8          Redis 7          RabbitMQ 4
    （唯一事实源）   （缓存/限流/锁）   （异步解耦）
```

## 2. 后端包结构

```text
services/api
  auth         登录、JWT、刷新令牌、RBAC
  user         用户、角色、个人资料
  event        活动、分类、发布、审核
  registration 报名、取消、候补、递补
  checkin      签到会话、二维码、签到记录
  notification 站内信、邮件、推送
  analytics    Matomo 事件记录、统计查询
  admin        管理后台接口
  common       异常、响应、分页、审计、Outbox
  infra        Redis、RabbitMQ、JPA 配置
```

## 3. 各组件职责边界

### MySQL —— 唯一事实源

所有权威数据落 MySQL，容量判定、报名唯一性约束、状态流转都以数据库为准。
`event.capacity` 配合 `version` 字段做乐观锁；`registration` 上对 `(event_id, user_id)` 建唯一索引保证「一人一活动一条有效报名」。

### Redis —— 只做加速与协调，丢失不致命

| 用途 | Key 形态 | TTL | 状态 |
|---|---|---|---|
| 活动详情缓存 | `cache:event:detail:{id}` | 60s | ✅ 已实现 |
| 签到二维码 token | `checkin:session:{token}` | 60s | ✅ 已实现 |
| 接口限流 | `rate:{scope}:{subject}` | 窗口长度 | ✅ 已实现 |
| Refresh Token 黑名单 | `jwt:blacklist:{jti}` | 至过期 | ✅ 已实现 |
| 热门活动列表 | `event:hot` | 1m | 待做 |
| 报名人数计数 | `event:{id}:reg_count` | 跟随活动 | 待做（当前一律查库） |

**原则：先判断 Redis 里存的是不是唯一的事实来源，再决定故障时往哪边倒。**

- 只是优化（缓存、限流）→ **fail-open**，记 WARN 后回落。Redis 挂掉不该把登录入口
  一起打挂。`RateLimitService` 和 `EventDetailCache` 都是这个策略。
- 是唯一事实来源（签到令牌）→ **fail-closed**，直接报错。放行等于让无法验证的
  令牌通过。`CheckInTokenService` 是这个策略。

两处取舍不同是有意为之，不是不一致。

**活动详情缓存只缓存已发布活动。** 草稿的可见性取决于调用者身份（组织者能看、
他人 404），把草稿塞进缓存会让第二个访问者绕过归属检查直接拿到内容——
这是缓存引入的越权漏洞，且不会有任何报错。TTL 取 60 秒而非策划写的 5 分钟，
是因为缓存值里含「剩余名额」而报名随时在变。**但缓存只影响展示，不影响正确性**：
名额是否真的还有，是在报名时于数据库行锁下重新判定的，所以再陈旧的缓存也不会超卖。

### RabbitMQ —— Outbox 模式异步解耦

业务事务内只写 `outbox_event` 表，再由定时任务（或 CDC）投递到 MQ，避免「消息发出去了但事务回滚」。

队列：

| 队列 | 用途 |
|---|---|
| `notification.queue` | 报名成功、候补转正、活动取消、活动提醒 |
| `audit.queue` | 审计日志异步落库 |
| `analytics.queue` | 统计事件异步处理 |
| `dead.letter.queue` | 失败消息 |

#### 实际实现

**投递**：业务代码在**自己的事务里**只往 `outbox_events` 写一行，不直接发 MQ。
发消息和提交数据库无法组成原子操作——先发后提交，回滚后下游收到不存在的变更；
先提交后发，进程崩溃就永久丢消息。写进同一张表则两者同生共死。

由 `OutboxPublisher` 每 2 秒轮询 `PENDING` 事件转发到 RabbitMQ。投递语义是
**至少一次**：发完消息、事务未提交时崩溃，恢复后会重发。消费端用消息里的
`id`（即 outbox 主键）做幂等，落库表的唯一索引是并发兜底。

多实例部署时用 `SELECT … FOR UPDATE SKIP LOCKED` 取批次，实例之间不互相阻塞、
也不会重复投递同一条。重试用尽（5 次）的事件转 `FAILED` 并停止投递，等人工介入——
无休止重试一条永远失败的消息只会淹没日志。

**消费**：`notification.queue` → `NotificationConsumer`，`audit.queue` → `AuditConsumer`。
`analytics.queue` 拓扑已声明，消费者待 Matomo 接入时补上。

用**自动 ACK + 重试拦截器**（3 次指数退避）而不是策划里写的手动 ACK：
手动 ACK 下要么无限 requeue（热循环打满 CPU），要么一失败就进死信队列——
一次数据库瞬断就会让「活动取消」这类通知直接进 DLQ 且用户永远收不到。
自动 ACK 配合重试让瞬断自愈，只有真正处理不了的才进 `dead.letter.queue`。

**消费者的两个已知取舍**：

1. 事件负载里**快照**了渲染通知所需的全部数据（活动标题、时间、收件人名单），
   消费端不回查业务表。回查会让「重放历史事件」读到现在的数据而通知错人。
   代价是负载随活动规模增长，万人活动需要改成分片投递。
2. 邮件发送发生在数据库事务内（`NotificationConsumer#deliver`），会拉长事务持有
   连接的时间。站内信已落库，邮件失败不回滚——不该因为邮件服务器抽风让整条消息重试。
   若邮件量上来，应改为事务提交后再发。

### Matomo —— 与主业务完全解耦

- Web 用 Matomo JS 跟踪页面浏览和自定义事件。
- Expo 用 HTTP Tracking API 或兼容 RN 的 Matomo 客户端。
- 关键事件：`view_event`、`register_event`、`cancel_registration`、`checkin_success`。
- **Matomo 挂掉不能影响报名**：埋点调用全部异步、失败静默吞掉。
- 开启 IP 匿名化，注意隐私合规。

## 4. API 设计草案

统一前缀 `/api/v1`。

**认证**

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`

**用户**

- `GET /me`
- `PATCH /me`
- `GET /me/registrations`
- `GET /me/notifications`

**活动**

- `GET /events`
- `GET /events/{id}`
- `POST /events`（组织者）
- `PATCH /events/{id}`（组织者）
- `POST /events/{id}/publish`
- `POST /events/{id}/cancel`

**报名**

- `POST /events/{id}/registrations`
- `DELETE /registrations/{id}`
- `GET /events/{id}/registrations`（组织者）

**签到**

- `POST /events/{id}/checkin-session` —— 生成动态二维码
- `POST /checkins/scan` —— 扫码签到
- `GET /events/{id}/checkins` —— 签到统计

**管理**

- `GET /admin/users`
- `GET /admin/events`
- `POST /admin/events/{id}/approve`
- `GET /admin/audit-logs`

## 5. 安全

- JWT Access Token 15 分钟，Refresh Token 7 天。
- Web 推荐 HttpOnly Cookie 存 Refresh Token；移动端用 Expo SecureStore。
- Spring Security + RBAC；Bean Validation；CORS 白名单；限流；审计日志。
- 密码用 BCrypt 或 Argon2。
- 所有密钥走环境变量，禁止硬编码。
