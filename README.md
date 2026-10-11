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

## 怎么运行

有两种方式，用途不同。

### 方式一：本地一体化（想看到整个系统跑起来）

一条命令，整套栈跑在 Docker 里，入口是 Nginx：

```bash
make local-up
```

打开 **http://localhost/** 即可。无需域名、无需证书。

首次会构建 api / web 镜像（几分钟），之后启动约 30 秒。这个脚本做的是：
用 `.env.example` 的占位配置、渲染一份不带 TLS 的 Nginx 配置、启动全部 9 个容器。

```bash
make prod-ps       # 查看各容器状态与健康检查
make prod-logs     # 跟踪日志
make local-down    # 停止（保留数据）
make local-reset   # 停止并清空所有数据
```

> **为什么不用 `make prod-up`**：那是给真实服务器用的，Nginx 会加载 443 的配置，
> 而 443 需要证书；本地没有域名签不出证书，栈会直接起不来。
> `make local-up` 渲染的是功能完整、只是没有 TLS 的那份配置。

### 方式二：开发模式（改代码时用）

代码跑在宿主机上，有热重载；只有数据服务在 Docker 里。需要三个终端：

```bash
# 终端 1
make up            # MySQL / Redis / RabbitMQ / Matomo

# 终端 2
make api           # 后端 :8080（接口文档 http://localhost:8080/swagger-ui.html）

# 终端 3
make web           # Web :3000
```

移动端另开一个终端：`make mobile`（用 Expo Go 扫码，或按 i / a 开模拟器）。

> 本机 3306 若已被其他 MySQL 占用，`make up` 会失败。改用其他端口：
> `MYSQL_PORT=13306 make up`，然后 `MYSQL_PORT=13306 make api`。

### 前置条件

Node.js ≥ 20（npm ≥ 11）、JDK 17、Maven ≥ 3.9、Docker + Docker Compose。

> `.env.example` 的读写权限在 `.claude/settings.json` 中显式放行，`.env` 保持拒绝——避免真实密钥被误写或误读。

服务端口：MySQL `3306`、Redis `6379`、RabbitMQ `5672` / 管理台 `15672`、Matomo 由 Nginx 反代到 `/matomo`。

## 开发进度

- [x] **阶段 0** 项目初始化：monorepo 目录、文档、`.env.example`、基础 docker-compose
- [x] **阶段 0.5** Web/Mobile 脚手架：Next.js 16 + Tailwind 4 / Expo 57 + Expo Router，均已通过构建与类型检查（后端前置工作）
- [x] **阶段 1** 后端骨架：Spring Boot 3.5 + Java 17 + Security + JPA + Flyway + springdoc
- [x] **阶段 2** 核心业务：活动 CRUD、报名、候补递补、并发防超卖、扫码签到
- [x] **阶段 3** 异步与缓存：Outbox + RabbitMQ 通知/审计、Redis 缓存与限流、死信队列
- [x] **阶段 4** Web 端：公开页面（Server Components）、登录注册、我的报名与票券、通知、组织者管理台、Matomo 埋点
- [x] **阶段 5** 移动端：Expo 57 + Expo Router，活动浏览/报名、票券二维码、扫码签到、通知、个人中心
- [x] **阶段 6** 部署：多阶段 Dockerfile（api / web）、Nginx 反代与 HTTPS、Certbot 签发与续期、生产编排
- [x] **阶段 7** 测试与验收：后端 51 个 Testcontainers 集成测试、Web 12 个 Playwright 端到端测试、移动端 22 个 Jest 单测、CI 三端并行

详细策划见 [`docs/project-plan.md`](docs/project-plan.md)，架构说明见 [`docs/architecture.md`](docs/architecture.md)，
库表设计见 [`docs/database.md`](docs/database.md)，部署见 [`docs/deployment.md`](docs/deployment.md)。

接口规范 [`docs/openapi.yaml`](docs/openapi.yaml) 由 springdoc 在运行时从 Controller 生成，
是**产物不是手写文档**——手写的规范改第一个接口就开始漂移。要更新它：

```bash
make up && make api     # 另开一个终端跑 make api
make openapi            # 导出到 docs/openapi.yaml
```

开发时直接看 http://localhost:8080/swagger-ui.html 更方便。

## 前端开发

```bash
make up          # 起 MySQL / Redis / RabbitMQ
make api         # 另开终端，起后端（:8080）
make web         # 再开一个终端，起 Next.js（:3000）
```

### 页面

| 路径 | 说明 | 渲染方式 |
|---|---|---|
| `/` | 首页 + 近期活动 | Server Component |
| `/events`、`/events/[id]` | 活动列表 / 详情 | Server Component（骨架预渲染 + 流式） |
| `/login`、`/register` | 登录 / 注册 | Client |
| `/me`、`/me/registrations`、`/me/notifications` | 个人中心 / 我的报名与票券 / 通知 | Client（需登录） |
| `/organizer/events`、`/new`、`/[id]` | 活动管理、创建、报名名单与签到 | Client（需登录） |

### 认证方式

访问令牌**只存在内存**，不写 localStorage —— 那里存的东西任何注入的 JS 都能读走。
刷新令牌放在 **HttpOnly Cookie** 里，JS 看不见；页面刷新后由它换一个新的访问令牌。
访问令牌 15 分钟过期，客户端在收到 401 时自动刷新并重放请求（单飞，避免并发刷新
触发后端的令牌重用检测）。

### 尚未实现

**管理端页面（用户管理、活动审核、审计日志、系统配置）没有做**，因为后端还没有
`/admin/*` 接口。这几个页面需要先补后端管理接口，否则只能做出点了没反应的假页面。

## 移动端

```bash
make up && make api      # 后端
make mobile              # 启动 Expo（扫码用 Expo Go 打开，或按 i / a 开模拟器）
```

移动端连的是 `EXPO_PUBLIC_API_BASE_URL`。用真机调试时要填**本机局域网 IP**，
不能是 `localhost`——手机上的 localhost 指向手机自己：

```bash
EXPO_PUBLIC_API_BASE_URL=http://192.168.1.x:8080/api/v1 make mobile
```

### 屏幕

| 路由 | 说明 |
|---|---|
| `/(tabs)/index` | 活动列表（无限滚动 + 下拉刷新） |
| `/events/[id]` | 活动详情、报名/取消 |
| `/(tabs)/tickets` | 我的报名与电子票券（含二维码） |
| `/(tabs)/notifications` | 通知 |
| `/(tabs)/profile` | 个人中心 |
| `/scan` | 扫码签到（相机） |
| `/organizer` | 我组织的活动（只读 + 发布） |

### 令牌存储与 Web 不同

移动端没有 HttpOnly Cookie 可用，刷新令牌存在 **SecureStore**（iOS Keychain /
Android Keystore），访问令牌只在内存。用 SecureStore 而不是 AsyncStorage 的原因：
后者在已 root 的设备上是明文可读的，而刷新令牌有效期 7 天，拿到它就等于长期接管账号。

**创建和编辑活动不在移动端**——策划里移动端的范围是浏览、报名、票券、扫码和通知，
活动管理属于 Web 端。与其做一个字段残缺的管理页，不如让 `/organizer` 保持只读。

## 测试

| 层 | 工具 | 数量 | 命令 |
|---|---|---|---|
| 后端 | JUnit + Testcontainers（真实 MySQL/Redis/RabbitMQ） | 51 | `cd services/api && ./mvnw verify` |
| Web 端到端 | Playwright（真实浏览器 + 真实后端） | 12 | `make e2e` |
| 移动端 | Jest | 22 | `npm test --workspace apps/mobile` |

后端集成测试全部跑在真实中间件上，不用 H2 或嵌入式替身——本项目最容易出错的地方
（并发超卖、事务传播、唯一索引、缓存失效）恰恰只在真实实现上才暴露。三处真 bug
就是这么找到的：泄露处置被自己的异常回滚、匿名浏览被 401 拦下、限流开关读不到环境变量。

**端到端测试必须重置数据。** E2E 会不断创建活动，而活动列表一页只显示 12 条，
跑几轮之后新建的活动被挤到第 2 页，「它应当出现在列表里」这条断言就开始随机失败——
看起来像产品坏了，实际是测试数据在累积。`make e2e` 会先清空数据库与 Redis 再跑。

E2E 跑的是 `standalone` server（生产部署用的同一个），不是 `next start`。
用后者测出来的结果属于另一个产物，生产上出问题的地方测试覆盖不到。

## 约定

- 不硬编码任何密钥，全部走环境变量；`.env` 不入库。
- 数据库结构变更一律通过 Flyway 迁移，禁止手改线上表。
- 接口以 `docs/openapi.yaml` 为准，前端据此生成类型。
- 每个阶段结束更新本 README 的进度勾选，并跑通该阶段测试。

### 已知的工程约束

- **`@types/react` 必须全局唯一**。根 `package.json` 的 `overrides` 把它锁到单一版本，因为 web 与 mobile 声明区间不同会被 npm 装成两份嵌套副本，导致 "Two different types with this name exist" 的假类型错误。改动依赖后若类型报错，先确认 `find . -path "*@types/react/package.json"` 只剩一份。
- **`apps/mobile/expo-env.d.ts`** 由 Expo 生成、默认被 gitignore。仓库里保留了一份，缺失时 `.css` / `.module.css` 导入会报找不到模块。
