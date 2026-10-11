#!/usr/bin/env bash
#
# 首次签发 Let's Encrypt 证书，并把 Nginx 从 HTTP-only 切到 HTTPS。
#
# 整个流程存在的理由是那个死锁：
#   443 的 server 块需要证书文件 → 证书文件需要 Certbot 签发 →
#   Certbot 的 HTTP-01 验证需要 Nginx 在 80 端口提供服务 → Nginx 启动又需要证书。
#
# 解法是先用一份不含 443 的配置把 Nginx 跑起来，签发完成后再换成完整配置。
#
# 用法：
#   bash scripts/init-letsencrypt.sh            # 正式签发
#   STAGING=1 bash scripts/init-letsencrypt.sh  # 用 Let's Encrypt 测试环境试跑
#
# 强烈建议先用 STAGING=1 跑一遍。正式环境有速率限制：
# 同一域名每周 50 张证书、失败验证每小时 5 次，调错几次就会被锁住一整天。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

COMPOSE_FILES=(-f infra/docker-compose.yml -f infra/docker-compose.prod.yml)
COMPOSE=(docker compose "${COMPOSE_FILES[@]}" --env-file .env)

STAGING="${STAGING:-0}"
NGINX_TEMPLATES="infra/nginx/templates"
NGINX_CONF_DIR="infra/nginx/conf.d"

if [[ ! -f .env ]]; then
    echo "缺少 .env。请先执行：cp .env.example .env 并填写 APP_DOMAIN / CERTBOT_EMAIL" >&2
    exit 1
fi

# shellcheck disable=SC1091
set -a; source .env; set +a

if [[ -z "${APP_DOMAIN:-}" || "${APP_DOMAIN}" == "example.com" ]]; then
    echo "请先在 .env 里把 APP_DOMAIN 设成真实域名（当前：${APP_DOMAIN:-未设置}）" >&2
    exit 1
fi
if [[ -z "${CERTBOT_EMAIL:-}" ]]; then
    echo "请先在 .env 里设置 CERTBOT_EMAIL" >&2
    exit 1
fi

render() {
    local template="$1" output="$2"
    # envsubst 只替换列出的变量，避免误伤 nginx 自己的 $host / $remote_addr
    APP_DOMAIN="${APP_DOMAIN}" envsubst '${APP_DOMAIN}' < "${template}" > "${output}"
}

echo "==> 1/5 渲染 HTTP-only 配置（证书还不存在，此时不能出现 443 的 server 块）"
mkdir -p "${NGINX_CONF_DIR}"
render "${NGINX_TEMPLATES}/proxy-headers.inc.template" "${NGINX_CONF_DIR}/proxy-headers.inc"
render "${NGINX_TEMPLATES}/http-only.conf.template" "${NGINX_CONF_DIR}/default.conf"

echo "==> 2/5 启动 Nginx，让 80 端口先能响应 challenge"
"${COMPOSE[@]}" up -d nginx

echo "==> 3/5 等待 Nginx 就绪"
sleep 3

echo "==> 4/5 申请证书（域名：${APP_DOMAIN}）"
STAGING_ARGS=()
if [[ "${STAGING}" == "1" ]]; then
    STAGING_ARGS=(--staging)
    echo "    注意：这是测试环境签发的证书，浏览器不会信任。仅用于验证流程。"
fi

"${COMPOSE[@]}" run --rm --entrypoint certbot certbot certonly \
    --webroot -w /var/www/certbot \
    --email "${CERTBOT_EMAIL}" \
    --agree-tos --no-eff-email \
    --keep-until-expiring \
    "${STAGING_ARGS[@]+"${STAGING_ARGS[@]}"}" \
    -d "${APP_DOMAIN}"

echo "==> 5/5 切换到 HTTPS 配置并重载 Nginx"
render "${NGINX_TEMPLATES}/https.conf.template" "${NGINX_CONF_DIR}/default.conf"
"${COMPOSE[@]}" exec nginx nginx -s reload

echo
echo "完成。https://${APP_DOMAIN} 现在应当可访问。"
echo "续期由 certbot 容器每 12 小时自动检查一次（见 docker-compose.prod.yml）。"
if [[ "${STAGING}" == "1" ]]; then
    echo
    echo "提醒：本次是测试证书。确认流程无误后，先删掉测试证书再正式签发："
    echo "  docker compose ${COMPOSE_FILES[*]} run --rm --entrypoint certbot certbot delete --cert-name ${APP_DOMAIN}"
fi
