#!/usr/bin/env bash
#
# 清空业务数据，为端到端测试准备一个干净的起点。
#
# 为什么必须这么做：E2E 会不断创建活动，而活动列表一页只显示 12 条。
# 跑几次之后新创建的活动就被挤到第 2 页，断言「它在列表里」会开始随机失败——
# 看起来像产品坏了，实际是测试数据在累积。共享数据库的 E2E 套件都有这个问题。
#
# 只删数据，不动表结构：这样不必重跑 Flyway，也不必重启后端。
#
# 用法：bash scripts/reset-test-data.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

ENV_FILE="${ENV_FILE:-.env}"
if [[ ! -f "${ENV_FILE}" ]]; then
    ENV_FILE=".env.example"
fi

# 逐行解析，刻意不用 `source`。
# .env 里的值不保证是合法的 shell 语法——例如
#   SMTP_FROM=EventHub <noreply@example.com>
# 里的 `<` 会被 bash 当成重定向符，source 直接报语法错误并中断脚本。
# 用 `source` 加载 env 文件是常见的做法，但只在所有值都规规矩矩时才成立。
read_env() {
    local key="$1" fallback="${2:-}"
    local value
    value="$(grep -E "^${key}=" "${ENV_FILE}" | tail -1 | cut -d= -f2-)"
    printf '%s' "${value:-${fallback}}"
}

MYSQL_ROOT_PASSWORD="$(read_env MYSQL_ROOT_PASSWORD)"
MYSQL_DATABASE="$(read_env MYSQL_DATABASE eventhub)"
REDIS_PASSWORD="$(read_env REDIS_PASSWORD)"

echo "==> 清空业务表（保留表结构与 Flyway 历史）"
docker compose -f infra/docker-compose.yml --env-file "${ENV_FILE}" exec -T mysql \
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE check_ins;
TRUNCATE TABLE outbox_events;
TRUNCATE TABLE notifications;
TRUNCATE TABLE audit_logs;
TRUNCATE TABLE registrations;
TRUNCATE TABLE events;
TRUNCATE TABLE refresh_tokens;
TRUNCATE TABLE user_roles;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;
SQL

echo "==> 清空 Redis"
# 活动详情缓存必须清掉，否则测试读到的是上一轮留下的活动。
# 顺带把限流计数和令牌黑名单也清了——测试环境里这些都该归零。
docker compose -f infra/docker-compose.yml --env-file "${ENV_FILE}" exec -T redis \
    redis-cli -a "${REDIS_PASSWORD}" --no-auth-warning FLUSHDB > /dev/null

echo "==> 完成，数据库与缓存已重置"
