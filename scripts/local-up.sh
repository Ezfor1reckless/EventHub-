#!/usr/bin/env bash
#
# 本地一体化启动整套栈：Nginx + Web + API + MySQL + Redis + RabbitMQ + Matomo。
#
# 与 make prod-up 的区别只有一处：不启用 HTTPS。
# 本地没有真实域名，签不出证书；没有证书，443 的 server 块会让 Nginx 起不来。
# 所以这里渲染的是 local-http.conf.template——功能完整、只是没有 TLS。
#
# 适用场景：想看到整个系统跑起来的样子，或者验证「镜像 + 编排 + 反代」这条链路。
# 日常写代码请用开发模式（make up && make api && make web），有热重载，改一行不用重建镜像。
#
# 用法：bash scripts/local-up.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

# 有 .env 就用它（用户可以自定义端口、密码），没有就用示例值。
# 示例值里的密码都是 change-me-* —— 本地开发可以接受，绝不能用在上线环境。
ENV_FILE=".env"
if [[ ! -f "${ENV_FILE}" ]]; then
    ENV_FILE=".env.example"
    echo "未找到 .env，使用 .env.example 的占位配置（本地开发可以，生产不行）"
fi

# 这几个值必须按本地情况覆盖：.env.example 里写的是线上域名。
export APP_DOMAIN="${APP_DOMAIN:-localhost}"
export API_BASE_URL="${API_BASE_URL:-http://localhost/api/v1}"
export MATOMO_URL="${MATOMO_URL:-http://localhost/matomo}"
export MATOMO_SITE_ID="${MATOMO_SITE_ID:-1}"
# 走 http 时 Cookie 不能带 Secure 标记，否则浏览器直接丢弃，
# 表现为「登录成功但一刷新就变回未登录」。
export AUTH_COOKIE_SECURE=false

echo "==> 渲染 Nginx 本地配置（域名：${APP_DOMAIN}）"
mkdir -p infra/nginx/conf.d
APP_DOMAIN="${APP_DOMAIN}" envsubst '${APP_DOMAIN}' \
    < infra/nginx/templates/local-http.conf.template \
    > infra/nginx/conf.d/default.conf
APP_DOMAIN="${APP_DOMAIN}" envsubst '${APP_DOMAIN}' \
    < infra/nginx/templates/proxy-headers.inc.template \
    > infra/nginx/conf.d/proxy-headers.inc

echo "==> 构建并启动（首次构建镜像需要几分钟）"
docker compose \
    -f infra/docker-compose.yml \
    -f infra/docker-compose.prod.yml \
    --env-file "${ENV_FILE}" \
    up -d --build

echo "==> 等待服务就绪"
for i in $(seq 1 90); do
    if curl -sf -o /dev/null "http://${APP_DOMAIN}/api/v1/events?size=1" 2>/dev/null; then
        echo
        echo "就绪。"
        echo
        echo "  Web      http://${APP_DOMAIN}/"
        echo "  API      http://${APP_DOMAIN}/api/v1/events"
        echo "  Matomo   http://${APP_DOMAIN}/matomo/"
        echo
        echo "  接口文档（Swagger UI）没有经 Nginx 暴露，这是刻意的——"
        echo "  生产环境把全量接口文档挂公网等于给攻击者一份说明书。"
        echo "  需要看的话用开发模式（make api）直连 8080 端口的 /swagger-ui.html。"
        echo
        echo "  查看状态：make prod-ps"
        echo "  查看日志：make prod-logs"
        echo "  停止：    make local-down"
        exit 0
    fi
    sleep 5
done

echo "等待超时。下面是没有就绪的容器日志：" >&2
docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml \
    --env-file "${ENV_FILE}" ps >&2
docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml \
    --env-file "${ENV_FILE}" logs --tail=30 api nginx >&2
exit 1
