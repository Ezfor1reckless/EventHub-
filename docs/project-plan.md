# EventHub 项目策划

## 1. 项目定位

**EventHub — 社区活动报名与签到平台**

核心闭环：

> 组织者发布活动 → 用户浏览/报名 → 满员进入候补 → 系统异步通知 → 活动前提醒 → 现场扫码签到 → 统计签到率 → Matomo 分析转化。

### 角色

| 角色 | 职责 |
|---|---|
| 普通用户 USER | 浏览、报名、取消、候补、查看票券、签到 |
| 组织者 ORGANIZER | 创建活动、管理报名、生成签到二维码、查看统计 |
| 管理员 ADMIN | 用户管理、活动审核、系统配置、审计日志 |

## 2. MVP 范围

- 认证与权限（JWT + RBAC）
- 活动 CRUD
- 报名 / 取消 / 候补 / 自动递补
- 我的报名与电子票券
- 动态二维码签到
- 站内信 / 邮件通知
- 管理后台
- Matomo 基础埋点
- Docker Compose 一键部署

## 3. 后续扩展

支付、积分、评论、收藏、推荐、地图、Expo 推送、离线签到。

## 4. 部署拓扑

```text
Next.js Web 管理端/用户端
        │
Expo React Native 移动端
        │
        ▼
      Nginx
  /      /api      /matomo
  │        │          │
Web      Spring Boot  Matomo
          │
   ┌──────┼────────┐
 MySQL  Redis   RabbitMQ
          │
      通知/审计/统计消费者
```

部署原则：

- `apps/web`：Next.js，部署到 Docker。
- `apps/mobile`：Expo，**不放入 Docker Compose**，用 EAS Build 或 Expo Go。
- `services/api`：Spring Boot，Docker 部署。
- `infra`：Nginx、Certbot、MySQL、Redis、RabbitMQ、Matomo、Matomo DB。
- 移动端和 Web 都通过 `https://api.example.com/api/v1` 访问后端。

## 5. 核心领域模型

- `User`：id、email、phone、passwordHash、nickname、avatar、status
- `Role`：USER、ORGANIZER、ADMIN
- `Event`：id、title、description、cover、location、startAt、endAt、capacity、status、organizerId、version
- `Registration`：id、eventId、userId、status、ticketCode、registeredAt、cancelledAt
- `CheckIn`：id、registrationId、eventId、userId、checkedInAt、method、operatorId
- `Notification`：id、userId、type、title、content、channel、status
- `OutboxEvent`：id、aggregateType、aggregateId、eventType、payload、status
- `RefreshToken`：id、userId、tokenHash、expiresAt、revoked
- `AuditLog`：id、actorId、action、targetType、targetId、ip、createdAt

### 报名状态机

```text
PENDING → CONFIRMED → CHECKED_IN
        → WAITLISTED → CONFIRMED
        → CANCELLED
```

### 业务规则

- 活动容量以 MySQL 为准，Redis 只做缓存和限流。
- 同一用户同一活动只能有一条有效报名。
- 满员后进入候补；有人取消时，按候补顺序递补。
- 签到二维码动态生成，Redis 存 token，TTL 60 秒。
- 报名、签到、消息消费必须幂等。

## 6. 开发阶段

### 阶段 0：项目初始化

- 创建 monorepo 目录。
- 写 `README.md`、`docs/project-plan.md`。
- 写 `.env.example`。
- 写基础 `docker-compose.yml`：MySQL、Redis、RabbitMQ、Matomo。
- 不写业务代码。

### 阶段 1：后端骨架

- Spring Boot 3 + Java 17。
- 依赖：Web、Security、JPA、Validation、Redis、AMQP、Flyway、OpenAPI、Actuator。
- 配置 MySQL、Redis、RabbitMQ。
- 实现统一响应、异常处理、JWT、RBAC。
- 写 Flyway 初始迁移。
- 写 Testcontainers 集成测试。

### 阶段 2：核心业务

- 用户、活动、报名、候补、签到。
- 实现并发安全和幂等。
- 写 OpenAPI 文档。
- 写单元测试和集成测试。

### 阶段 3：异步与缓存

- Outbox + RabbitMQ。
- 通知消费者、审计消费者。
- Redis 缓存、限流、签到 token。
- 死信队列。

### 阶段 4：Web 端

- Next.js 页面和组件。
- API 客户端。
- 登录态、权限路由。
- Matomo 埋点。

### 阶段 5：移动端

- Expo 页面。
- 扫码签到。
- SecureStore。
- Matomo 埋点。

### 阶段 6：部署

- Nginx 配置。
- Certbot 证书。
- 生产 Docker Compose。
- 健康检查、日志、备份说明。

### 阶段 7：测试与验收

- 后端：JUnit、Testcontainers、MockMvc。
- Web：Playwright。
- 移动端：Jest + React Native Testing Library。
- CI：GitHub Actions。

## 7. MVP 验收标准

- [ ] 用户可以注册、登录、刷新 Token。
- [ ] 组织者可以创建并发布活动。
- [ ] 用户可以报名、取消、进入候补。
- [ ] 满员后候补可自动递补。
- [ ] 用户可以查看电子票券。
- [ ] 组织者可生成动态签到二维码。
- [ ] 移动端可扫码签到，重复签到被拒绝。
- [ ] 报名成功、候补转正、活动提醒通过 RabbitMQ 异步通知。
- [ ] Redis 用于缓存、限流、签到 token。
- [ ] Matomo 记录页面浏览和关键事件。
- [ ] Nginx + Certbot 提供 HTTPS。
- [ ] `docker compose up` 能启动核心服务。
- [ ] 后端、Web、移动端都有基础测试。
