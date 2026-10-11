# 部署

生产环境是 9 个容器：`nginx`、`certbot`、`web`、`api`、`mysql`、`redis`、`rabbitmq`、
`matomo`、`matomo-db`。编排拆成两份文件：

- `infra/docker-compose.yml` —— 数据服务，本地开发也用这份
- `infra/docker-compose.prod.yml` —— **覆盖文件**，加上应用层并把数据服务的端口收回

两份必须一起用，顺序不能反：

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml --env-file .env up -d
```

这就是 `make prod-up` 做的事。

## 为什么数据服务的端口要收回

基础文件为了本地开发把 3306 / 6379 / 5672 映到了宿主机。生产环境这是纯风险——
公网上的 3306 端口通常几小时内就会被扫到并开始爆破。

覆盖文件用 `!reset []` 清空这些端口映射：

```yaml
mysql:
  ports: !reset []
```

不能靠「在覆盖文件里不写 ports」来实现——Compose 对 `ports` 这类多值字段是
**追加**语义，不是替换。不写等于保留原来的；写了反而会叠加。`!reset` 是唯一
能真正清空的方式。

## 前置条件

1. 一台有公网 IP 的服务器，80 和 443 端口开放
2. 域名 A 记录已指向该服务器（**必须先解析生效**，否则 Certbot 的 HTTP-01 验证会失败）
3. 装好 Docker 与 Docker Compose
4. 仓库已克隆到服务器

## 首次部署

```bash
cp .env.example .env
```

编辑 `.env`，**至少**要改这几项：

| 变量 | 说明 |
|---|---|
| `APP_DOMAIN` | 真实域名。留着 `example.com` 脚本会直接拒绝运行 |
| `CERTBOT_EMAIL` | 证书到期提醒的接收邮箱 |
| `API_BASE_URL` | `https://<域名>/api/v1` |
| `MATOMO_URL` | `https://<域名>/matomo` |
| `JWT_SECRET` | `openssl rand -base64 48` 生成，**不要用示例值** |
| `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` | 强密码 |
| `REDIS_PASSWORD` / `RABBITMQ_PASSWORD` | 强密码 |
| `MATOMO_DB_PASSWORD` / `MATOMO_DB_ROOT_PASSWORD` | 强密码 |

然后：

```bash
# 1. 先起数据服务，让 MySQL 完成初始化（首次约 40 秒）
make prod-up

# 2. 签发证书。强烈建议先用测试环境跑一遍：
STAGING=1 make init-ssl
#    确认无误后正式签发：
make init-ssl
```

`make init-ssl` 做的事情：渲染 HTTP-only 的 Nginx 配置 → 启动 Nginx →
Certbot 通过 HTTP-01 验证签发证书 → 换成 HTTPS 配置 → reload Nginx。
详见 `scripts/init-letsencrypt.sh` 里的注释。

**为什么必须分两步**：443 的 server 块需要证书文件，而证书文件又需要 Nginx 先
在 80 端口提供服务。先用不含 443 的配置打破这个死锁。

> **关于速率限制**：Let's Encrypt 对同一域名限制每周 50 张证书、验证失败每小时 5 次。
> 调试阶段反复正式签发很容易被锁住一整天。所以先用 `STAGING=1`。
> 测试证书浏览器不信任，但流程完全一致。

`web` 镜像的构建需要联网下载 npm 依赖，首次约 3-5 分钟。

## 证书续期

`certbot` 容器每 12 小时检查一次，剩余有效期不足 30 天时自动续期。
证书有效期 90 天，所以正常情况下你不需要做任何事。

验证自动续期是否正常工作：

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml \
  run --rm --entrypoint certbot certbot renew --dry-run
```

## 日常操作

```bash
make prod-logs          # 跟踪全部日志
make prod-ps            # 查看容器状态与健康检查
make prod-config        # 校验编排配置（不启动任何东西，改完 compose 先跑这个）
make prod-down          # 停止（保留数据卷）
```

### 更新代码后重新部署

```bash
git pull
make prod-up            # 会重新构建变化的镜像并滚动替换
```

## 数据备份

**数据库是唯一的事实来源**，Redis 里的东西丢了都能重建，MySQL 不能。

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml \
  exec -T mysql mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --routines --triggers eventhub \
  | gzip > "eventhub-$(date +%F-%H%M).sql.gz"
```

`--single-transaction` 保证 InnoDB 表在备份期间不会被写操作撕裂，且不锁表。

建议放进 crontab 每天执行，并把备份传到异机或对象存储——
**只存在同一台机器上的备份不算备份**。

恢复：

```bash
gunzip -c eventhub-2026-10-11-0300.sql.gz | \
  docker compose ... exec -T mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" eventhub
```

## 健康检查

所有服务都配了 healthcheck，`make prod-ps` 能看到状态。应用层的两个：

- `api` → `GET /actuator/health`
- `web` → `GET /`

`nginx` 依赖它们启动（`depends_on`），所以证书签发脚本会等应用就绪。

## 排查

**Nginx 起不来**：多半是证书还不存在但配置里已经有 443。
先 `make init-ssl`，或临时把 `infra/nginx/conf.d/default.conf` 换回
`infra/nginx/templates/http-only.conf.template` 渲染出来的版本。

**Certbot 验证失败**：确认域名 A 记录已生效（`dig +short <域名>`），
且 80 端口从公网可达。Let's Encrypt 是从公网发起验证的。

**API 报数据库连不上**：数据服务首次启动时 MySQL 初始化要 40 秒左右，
`api` 容器的 `depends_on: condition: service_healthy` 会等它，但如果 MySQL
健康检查超时，看 `make prod-logs` 里 mysql 的日志。

**限流把所有用户当成同一个人**：检查 `TRUST_PROXY` 是否为 `true`
（生产编排里已设），以及 Nginx 是否用 `$remote_addr` **覆盖**了
`X-Forwarded-For`（见 `infra/nginx/templates/proxy-headers.inc.template`）。
用 `$proxy_add_x_forwarded_for` 追加的话，客户端可以伪造 IP 绕过限流。

## 已知局限

- **没有监控与告警。** 容器挂了不会有人知道，除非用户来报。生产部署前应接上
  日志聚合与告警（Prometheus + Grafana，或云厂商的托管方案）。
- **没有零停机部署。** `make prod-up` 会重建并替换容器，期间有秒级不可用。
- **单机部署。** 没有多副本、没有负载均衡。数据服务是单点。
- **备份需要自己配 cron 和异地存储**，脚本没提供。
