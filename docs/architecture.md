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

| 用途 | Key 形态 | TTL |
|---|---|---|
| 活动详情缓存 | `event:detail:{id}` | 5m |
| 热门活动列表 | `event:hot` | 1m |
| 报名人数计数 | `event:{id}:reg_count` | 跟随活动 |
| 签到二维码 token | `checkin:token:{eventId}:{nonce}` | 60s |
| 接口限流 | `rate:{scope}:{key}` | 滑动窗口 |
| 分布式锁 | `lock:registration:{eventId}:{userId}` | 10s |
| Refresh Token 黑名单 | `jwt:blacklist:{jti}` | 至过期 |

**原则**：Redis 命中失败一律回落到 MySQL，不允许出现「Redis 挂了就无法报名」。

### RabbitMQ —— Outbox 模式异步解耦

业务事务内只写 `outbox_event` 表，再由定时任务（或 CDC）投递到 MQ，避免「消息发出去了但事务回滚」。

队列：

| 队列 | 用途 |
|---|---|
| `notification.queue` | 报名成功、候补转正、活动取消、活动提醒 |
| `audit.queue` | 审计日志异步落库 |
| `analytics.queue` | 统计事件异步处理 |
| `dead.letter.queue` | 失败消息 |

要求：生产者确认（publisher confirm）、消费者手动 ACK、消费幂等（以 `outbox_event.id` 或业务键去重）、失败重试 + 死信队列。

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
