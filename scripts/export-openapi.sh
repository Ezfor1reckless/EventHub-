#!/usr/bin/env bash
#
# 从运行中的应用导出 OpenAPI 规范到 docs/openapi.yaml。
#
# 规范由 springdoc 在运行时从 Controller 注解生成，因此这个文件是**产物**而不是
# 手写文档——手写的规范会在第一次改接口时就开始漂移。要更新它，先启动应用再跑本脚本。
#
# 前置：make up && make api（另开一个终端）

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="${ROOT_DIR}/docs/openapi.yaml"
BASE_URL="${APP_BASE_URL:-http://localhost:8080}"

if ! curl -sf -o /dev/null --max-time 5 "${BASE_URL}/actuator/health"; then
    echo "应用未在 ${BASE_URL} 上运行。" >&2
    echo "请先执行：make up && make api" >&2
    exit 1
fi

curl -sf "${BASE_URL}/v3/api-docs.yaml" -o "${OUTPUT}"

echo "已导出 ${OUTPUT}"
echo "  $(wc -l < "${OUTPUT}" | tr -d ' ') 行, $(wc -c < "${OUTPUT}" | tr -d ' ') 字节"
