#!/usr/bin/env bash
# PMS 热部署脚本：代码更新 → 依赖 → 数据库迁移 → 静态文件 → HUP 优雅重载 → 健康检查
# 服务全程可用，Nginx 不需要 reload。
set -Eeuo pipefail

APP_DIR="${PMS_APP_DIR:-/opt/pms}"
SERVICE="${PMS_SERVICE:-pms}"
HEALTH_URL="${PMS_HEALTH_URL:-http://127.0.0.1:8000/login/}"
RETRIES="${PMS_HEALTH_RETRIES:-10}"
INTERVAL="${PMS_HEALTH_INTERVAL:-2}"
DRY_RUN=0
SKIP_GIT=0
SKIP_MIGRATE=0

usage() {
  cat <<'EOF'
用法: hot-deploy.sh [选项]

一键热部署（不停机发布）：
  1. 拉取代码（仅当 APP_DIR 是 git 仓库）
  2. 安装/校验依赖
  3. python manage.py check / migrate / collectstatic
  4. systemctl reload pms（或向 gunicorn.pid 发送 HUP）
  5. 轮询健康检查 URL 直到返回 200

选项:
  --dry-run        只打印将要执行的命令，不做任何改动
  --skip-git       跳过 git pull（代码已用其它方式同步）
  --skip-migrate   跳过数据库迁移（纯模板/静态资源改动时可加速）
  --app-dir DIR    应用目录，默认 /opt/pms
  --service NAME   systemd 服务名，默认 pms
  -h, --help       显示本帮助

可用环境变量:
  PMS_APP_DIR, PMS_SERVICE, PMS_HEALTH_URL,
  PMS_HEALTH_RETRIES, PMS_HEALTH_INTERVAL,
  GUNICORN_PIDFILE（非 systemd 场景的 pid 文件路径）
EOF
}

log()  { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
warn() { printf '[%s] 警告: %s\n' "$(date '+%H:%M:%S')" "$*" >&2; }
run() {
  if [ "$DRY_RUN" -eq 1 ]; then
    printf '  + %s\n' "$*"
  else
    "$@"
  fi
}

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run)       DRY_RUN=1 ;;
    --skip-git)      SKIP_GIT=1 ;;
    --skip-migrate)  SKIP_MIGRATE=1 ;;
    --app-dir)       APP_DIR="${2:?--app-dir 需要参数}"; shift ;;
    --service)       SERVICE="${2:?--service 需要参数}"; shift ;;
    -h|--help)       usage; exit 0 ;;
    *)               printf '未知参数: %s\n' "$1" >&2; usage; exit 2 ;;
  esac
  shift
done

PY="$APP_DIR/.venv/bin/python"
PID_FILE="${GUNICORN_PIDFILE:-$APP_DIR/run/gunicorn.pid}"
PREV_VERSION="unknown"

log "应用目录: $APP_DIR | systemd 服务: $SERVICE | 健康检查: $HEALTH_URL"
[ "$DRY_RUN" -eq 1 ] && log "DRY-RUN 模式：仅打印命令，不会修改任何文件"

# --- 0. 预检 ---
if [ "$DRY_RUN" -eq 0 ]; then
  [ -d "$APP_DIR" ] || { log "错误: 应用目录不存在: $APP_DIR"; exit 1; }
  [ -x "$PY" ] || { log "错误: 找不到虚拟环境 Python: $PY"; exit 1; }
  [ -f "$APP_DIR/manage.py" ] || { log "错误: 未找到 manage.py"; exit 1; }
  mkdir -p "$(dirname "$PID_FILE")"
fi

# --- 1. 记录当前版本，并拉取代码 ---
if [ -d "$APP_DIR/.git" ] && [ "$DRY_RUN" -eq 0 ]; then
  PREV_VERSION="$(git -C "$APP_DIR" rev-parse --short HEAD)"
  log "当前版本: $PREV_VERSION"
fi

if [ "$SKIP_GIT" -eq 1 ]; then
  log "跳过代码拉取（--skip-git）"
elif [ -d "$APP_DIR/.git" ] || [ "$DRY_RUN" -eq 1 ]; then
  run git -C "$APP_DIR" pull --ff-only
else
  log "未检测到 .git 目录，假定代码已由其它方式同步"
fi

# --- 2. 依赖 ---
run "$PY" -m pip install -r "$APP_DIR/requirements.txt"

# --- 3. 检查 / 迁移 / 静态文件 ---
run "$PY" "$APP_DIR/manage.py" check
if [ "$DRY_RUN" -eq 0 ]; then
  "$PY" "$APP_DIR/manage.py" check --deploy || warn "check --deploy 输出了安全提示，请人工确认"
else
  run "$PY" "$APP_DIR/manage.py" check --deploy
fi

if [ "$SKIP_MIGRATE" -eq 1 ]; then
  log "跳过数据库迁移（--skip-migrate）"
else
  # 迁移必须先于 HUP：重载期间新旧 worker 会短暂共存，迁移需向后兼容
  run "$PY" "$APP_DIR/manage.py" migrate --noinput
fi

run "$PY" "$APP_DIR/manage.py" collectstatic --noinput

# --- 4. 优雅重载 ---
log "触发 Gunicorn 优雅重载（HUP）"
if [ "$DRY_RUN" -eq 1 ]; then
  run kill -HUP "<gunicorn-master-pid>"
elif command -v systemctl >/dev/null 2>&1 && systemctl cat "${SERVICE}.service" >/dev/null 2>&1; then
  run sudo systemctl reload "$SERVICE"
elif [ -f "$PID_FILE" ]; then
  MASTER_PID="$(cat "$PID_FILE")"
  run kill -HUP "$MASTER_PID"
  run kill -0 "$MASTER_PID"
else
  log "错误: 未找到 systemd 服务 ${SERVICE}，也没有 pid 文件 $PID_FILE"
  exit 1
fi

# --- 5. 健康检查 ---
if [ "$DRY_RUN" -eq 1 ]; then
  run curl -fsS -o /dev/null -w "%{http_code}" "$HEALTH_URL"
  log "DRY-RUN 结束，未执行任何操作"
  exit 0
fi

log "等待服务就绪（最多 $RETRIES 次，间隔 ${INTERVAL}s）"
READY=0
for _ in $(seq 1 "$RETRIES"); do
  CODE="$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)"
  if [ "$CODE" = "200" ]; then READY=1; break; fi
  sleep "$INTERVAL"
done

if [ "$READY" -ne 1 ]; then
  log "健康检查失败：$HEALTH_URL 未返回 200"
  log "请检查: systemctl status $SERVICE、journalctl -u $SERVICE -n 100"
  log "回滚: git -C $APP_DIR reset --hard $PREV_VERSION 后重跑本脚本（数据库迁移不可逆，请先确认）"
  exit 1
fi

log "热部署完成，服务在线。上一版本: $PREV_VERSION"
log "如需回滚: git -C $APP_DIR reset --hard $PREV_VERSION 后重跑本脚本"
