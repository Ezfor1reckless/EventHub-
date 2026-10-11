# EventHub — 社区活动报名与签到平台

组织者发布活动 → 用户浏览/报名 → 满员进候补 → 异步通知 → 活动前提醒 → 现场扫码签到 → 统计签到率 → Matomo 分析转化。

## 技术栈

| 层 | 技术 |
|---|---|
| Web 管理端 / 用户端 | Next.js（App Router）+ React + TypeScript + Tailwind CSS |
| 移动端 | Expo + React Native + TypeScript + Expo Router |
| 后端 | Java 17 + Spring Boot 3 + Spring Data JPA + Spring Security |
| 数据与服务 | MySQL 8、Redis 7、RabbitMQ 4、Matomo 5 |
| 基础设施 | Docker Compose、Nginx、Let's Encrypt Certbot |

> 策划原文指定 Java 21，本机仅有 JDK 17，经确认统一降到 **Java 17**（Spring Boot 3.x 完整支持）。

## 目录结构

```
event-platform/
  apps/
    web/                Next.js Web 端
    mobile/             Expo 移动端
  services/
    api/                Spring Boot 后端
  infra/
    nginx/              Nginx 配置
    certbot/            证书申请与续期
    docker-compose.yml  基础数据服务
  docs/
    project-plan.md
    architecture.md
    openapi.yaml
    database.md
  .github/workflows/    CI
  Makefile
```

## 角色

- **普通用户**：浏览、报名、取消、候补、查看票券、签到
- **组织者**：创建活动、管理报名、生成签到二维码、查看统计
- **管理员**：用户管理、活动审核、系统配置、审计日志

## 快速开始

前置：Node.js ≥ 20、JDK 17、Maven ≥ 3.9、Docker + Docker Compose。

```bash
cp .env.example .env          # 填入真实配置，切勿提交
make up                       # 启动 MySQL / Redis / RabbitMQ / Matomo
make logs                     # 查看日志
make down                     # 停止
```

> `.env.example` 的读写权限在 `.claude/settings.json` 中显式放行，`.env` 保持拒绝——避免真实密钥被误写或误读。

服务端口：MySQL `3306`、Redis `6379`、RabbitMQ `5672` / 管理台 `15672`、Matomo 由 Nginx 反代到 `/matomo`。

## 开发进度

- [x] **阶段 0** 项目初始化：monorepo 目录、文档、`.env.example`、基础 docker-compose
- [x] **阶段 0.5** Web/Mobile 脚手架：Next.js 16 + Tailwind 4 / Expo 57 + Expo Router，均已通过构建与类型检查（后端前置工作）
- [x] **阶段 1** 后端骨架：Spring Boot 3.5 + Java 17 + Security + JPA + Flyway + springdoc
- [x] **阶段 2** 核心业务：活动 CRUD、报名、候补递补、并发防超卖、扫码签到
- [x] **阶段 3** 异步与缓存：Outbox + RabbitMQ 通知/审计、Redis 缓存与限流、死信队列
- [ ] **阶段 4** Web 端
- [ ] **阶段 5** 移动端
- [ ] **阶段 6** 部署：Nginx + Certbot + 生产 Compose
- [ ] **阶段 7** 测试与验收：JUnit / Testcontainers / Playwright / Jest

详细策划见 [`docs/project-plan.md`](docs/project-plan.md)，架构说明见 [`docs/architecture.md`](docs/architecture.md)，
库表设计见 [`docs/database.md`](docs/database.md)。

接口规范 [`docs/openapi.yaml`](docs/openapi.yaml) 由 springdoc 在运行时从 Controller 生成，
是**产物不是手写文档**——手写的规范改第一个接口就开始漂移。要更新它：

```bash
make up && make api     # 另开一个终端跑 make api
make openapi            # 导出到 docs/openapi.yaml
```

开发时直接看 http://localhost:8080/swagger-ui.html 更方便。

## 约定

- 不硬编码任何密钥，全部走环境变量；`.env` 不入库。
- 数据库结构变更一律通过 Flyway 迁移，禁止手改线上表。
- 接口以 `docs/openapi.yaml` 为准，前端据此生成类型。
- 每个阶段结束更新本 README 的进度勾选，并跑通该阶段测试。

### 已知的工程约束

- **`@types/react` 必须全局唯一**。根 `package.json` 的 `overrides` 把它锁到单一版本，因为 web 与 mobile 声明区间不同会被 npm 装成两份嵌套副本，导致 "Two different types with this name exist" 的假类型错误。改动依赖后若类型报错，先确认 `find . -path "*@types/react/package.json"` 只剩一份。
- **`apps/mobile/expo-env.d.ts`** 由 Expo 生成、默认被 gitignore。仓库里保留了一份，缺失时 `.css` / `.module.css` 导入会报找不到模块。
