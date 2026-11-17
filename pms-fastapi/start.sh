#!/usr/bin/env bash
# 便捷启动：加载 .env 并启动 uvicorn（开发模式带 --reload）
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

PORT="${SERVER_PORT:-8000}"
exec .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port "$PORT" --reload
