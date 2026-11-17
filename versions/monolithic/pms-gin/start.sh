#!/usr/bin/env bash
# 便捷启动：加载 .env，缺失 go.sum 时先整理依赖，再启动服务。
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# 国内网络建议使用 goproxy.cn；已有 GOPROXY 时尊重原值
export GOPROXY="${GOPROXY:-https://goproxy.cn,direct}"

if [ ! -f go.sum ]; then
  echo ">> 首次启动，生成 go.sum：go mod tidy"
  go mod tidy
fi

PORT="${SERVER_PORT:-8081}"
echo ">> PMS Gin 版启动于 http://127.0.0.1:${PORT}"
exec go run .
