#!/usr/bin/env bash
# 本地命令行热部署：在本机启动 Gunicorn，通过 HUP 优雅重载让代码改动即时生效。
# 无需 systemd / sudo / git / 外部监听工具；项目根目录由脚本位置自动推导。
# 注意：所有变量统一使用 ${VAR} 形式，避免 macOS 自带 bash 3.2 在
# $VAR 后紧跟中文标点时把多字节字符吞进变量名。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PY="${APP_DIR}/.venv/bin/python"
CONF="${APP_DIR}/deploy/gunicorn.conf.py"
RUN_DIR="${APP_DIR}/.run"
PID_FILE="${RUN_DIR}/gunicorn.pid"
LOG_FILE="${RUN_DIR}/gunicorn.log"
STAMP_FILE="${RUN_DIR}/.watch-stamp"
PORT="${PMS_PORT:-8000}"
WORKERS="${PMS_WORKERS:-2}"
GRACEFUL="${PMS_GRACEFUL_TIMEOUT:-5}"
BIND="127.0.0.1:${PORT}"
HEALTH_URL="http://127.0.0.1:${PORT}/login/"

usage() {
  cat <<'EOF'
用法: local-hot.sh [命令] [选项]

在本机（macOS / Linux）启动 Gunicorn，并通过 HUP 优雅重载让代码改动即时生效。
无需 systemd、sudo、git 或额外文件监听工具；项目根目录由脚本位置自动推导。

命令（默认 deploy）:
  deploy   一键本地热部署：check →（可选）migrate → HUP 重载 → 健康检查
           服务未启动时会自动启动
  start    后台启动 Gunicorn（pid: .run/gunicorn.pid，日志: .run/gunicorn.log）
  reload   向 master 发送 HUP，优雅重载新代码
  status   查看进程、监听地址与健康检查状态
  stop     优雅停止并清理 pid 文件与残留 worker
  watch    监听 .py / .html / .css 变化并自动 reload（每 2 秒轮询，零依赖）
  help     显示本帮助

选项（仅 deploy）:
  --migrate        额外执行数据库迁移（需本地 MySQL 已启动）
  --collectstatic  额外收集静态文件

环境变量:
  PMS_PORT              监听端口，默认 8000
  PMS_WORKERS           worker 数，默认 2
  PMS_GRACEFUL_TIMEOUT  本地优雅退出秒数，默认 5（生产由配置文件保持 60）

示例:
  ./deploy/local-hot.sh                 # 首次自动启动，之后每次都是热重载
  ./deploy/local-hot.sh deploy --migrate
  ./deploy/local-hot.sh watch           # 改文件自动生效，Ctrl+C 退出
  ./deploy/local-hot.sh status
  ./deploy/local-hot.sh stop
EOF
}

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { printf '[%s] 错误: %s\n' "$(date '+%H:%M:%S')" "$*" >&2; exit 1; }

port_listeners() { lsof -tiTCP:"${PORT}" -sTCP:LISTEN 2>/dev/null || true; }

# 只清理监听本端口的 Gunicorn 进程，避免误伤其它服务
cleanup_port() {
  local pids p cmd
  pids="$(port_listeners)"
  [ -n "${pids}" ] || return 0
  for p in ${pids}; do
    cmd="$(ps -p "${p}" -o command= 2>/dev/null || true)"
    case "${cmd}" in
      *gunicorn*) log "清理残留 Gunicorn 进程（pid ${p}）"; kill -TERM "${p}" 2>/dev/null || true ;;
    esac
  done
  sleep 1
  pids="$(port_listeners)"
  for p in ${pids}; do
    cmd="$(ps -p "${p}" -o command= 2>/dev/null || true)"
    case "${cmd}" in
      *gunicorn*) kill -KILL "${p}" 2>/dev/null || true ;;
    esac
  done
}

precheck() {
  [ -x "${PY}" ] || die "找不到虚拟环境 Python: ${PY}（先创建 .venv 并安装 requirements.txt）"
  "${PY}" -c "import gunicorn" >/dev/null 2>&1 || die "未安装 gunicorn，请执行: ${PY} -m pip install -r ${APP_DIR}/requirements.txt"
  [ -f "${CONF}" ] || die "缺少 Gunicorn 配置: ${CONF}"
  mkdir -p "${RUN_DIR}"
}

pid_of() {
  [ -f "${PID_FILE}" ] || return 1
  local p
  p="$(cat "${PID_FILE}" 2>/dev/null || true)"
  [ -n "${p}" ] || return 1
  kill -0 "${p}" 2>/dev/null || return 1
  printf '%s' "${p}"
}

is_running() { pid_of >/dev/null 2>&1; }

health_check() {
  local i
  for i in $(seq 1 15); do
    # 只要能建立 HTTP 连接即视为服务在线（500 也算进程存活，业务错误另查日志）
    if curl -s -o /dev/null --max-time 2 "${HEALTH_URL}" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  return 1
}

start() {
  precheck
  if is_running; then
    local running_pid
    running_pid="$(pid_of)"
    log "服务已在运行（pid ${running_pid}）"
    return 0
  fi
  if [ -n "$(port_listeners)" ]; then
    log "端口 ${PORT} 已被其它进程占用："
    lsof -nP -iTCP:"${PORT}" -sTCP:LISTEN >&2 || true
    exit 1
  fi
  log "启动本地 Gunicorn：${BIND}，workers=${WORKERS}"
  (
    cd "${APP_DIR}" || exit 1
    export GUNICORN_BIND="${BIND}" GUNICORN_WORKERS="${WORKERS}" \
           GUNICORN_PIDFILE="${PID_FILE}" GUNICORN_GRACEFUL_TIMEOUT="${GRACEFUL}"
    exec nohup "${PY}" -m gunicorn -c "${CONF}" config.wsgi:application >> "${LOG_FILE}" 2>&1
  ) &
  local i
  for i in $(seq 1 20); do
    if is_running; then
      local new_pid
      new_pid="$(pid_of)"
      log "已启动（pid ${new_pid}），日志：${LOG_FILE}"
      return 0
    fi
    sleep 0.5
  done
  log "启动失败，日志尾部："
  tail -n 20 "${LOG_FILE}" >&2 || true
  return 1
}

reload() {
  if ! is_running; then
    log "服务未运行，改为启动"
    start
    return
  fi
  local pid
  pid="$(pid_of)"
  log "发送 HUP 到 master（pid ${pid}），优雅重载中"
  kill -HUP "${pid}" || die "发送 HUP 失败"
  sleep 2
  if ! health_check; then
    log "重载后健康检查未通过，请查看日志：${LOG_FILE}"
    tail -n 20 "${LOG_FILE}" >&2 || true
    return 1
  fi
  log "重载完成，服务在线：${HEALTH_URL}"
}

status() {
  if ! is_running; then
    log "未运行（pidfile: ${PID_FILE}）"
    return 1
  fi
  local pid code
  pid="$(pid_of)"
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "${HEALTH_URL}" || true)"
  log "运行中：pid=${pid} | 监听 ${BIND} | workers=${WORKERS}"
  log "健康检查 ${HEALTH_URL} → HTTP ${code:-无响应}"
  log "日志：${LOG_FILE}（tail -f ${LOG_FILE}）"
}

stop() {
  if is_running; then
    local pid i
    pid="$(pid_of)"
    log "停止服务（pid ${pid}）"
    kill -TERM "${pid}" || true
    for i in $(seq 1 30); do
      if ! kill -0 "${pid}" 2>/dev/null && [ -z "$(port_listeners)" ]; then break; fi
      sleep 0.5
    done
  else
    log "服务未运行"
  fi
  cleanup_port
  rm -f "${PID_FILE}"
  if [ -n "$(port_listeners)" ]; then
    log "警告：端口 ${PORT} 仍被其它进程占用（非 Gunicorn，未处理）"
  else
    log "已停止，端口 ${PORT} 已释放"
  fi
}

watch() {
  start || return 1
  touch "${STAMP_FILE}"
  log "监听 .py / .html / .css 变化（每 2 秒轮询），Ctrl+C 退出"
  while true; do
    local changed
    changed="$(find "${APP_DIR}" -type f \( -name '*.py' -o -name '*.html' -o -name '*.css' \) \
      -not -path '*/.venv/*' -not -path '*/.git/*' -not -path '*/__pycache__/*' \
      -not -path '*/.run/*' -not -path '*/staticfiles/*' \
      -newer "${STAMP_FILE}" -print 2>/dev/null | head -n 1 || true)"
    if [ -n "${changed}" ]; then
      touch "${STAMP_FILE}"
      log "检测到变化：${changed#"${APP_DIR}"/} → 自动重载"
      reload || log "重载失败，继续监听（查看日志 ${LOG_FILE}）"
    fi
    sleep 2
  done
}

deploy() {
  precheck
  log "本地热部署：项目 ${APP_DIR}"
  (cd "${APP_DIR}" && "${PY}" manage.py check) || die "manage.py check 未通过，已中止（未触发重载）"
  if [ "${OPT_MIGRATE}" -eq 1 ]; then
    (cd "${APP_DIR}" && "${PY}" manage.py migrate --noinput) || die "数据库迁移失败，已中止"
  fi
  if [ "${OPT_COLLECT}" -eq 1 ]; then
    (cd "${APP_DIR}" && "${PY}" manage.py collectstatic --noinput) || die "collectstatic 失败，已中止"
  fi
  if is_running; then
    reload
  else
    start || die "启动失败"
    health_check || die "启动后健康检查未通过"
    log "首次启动完成，服务在线：${HEALTH_URL}"
  fi
  status || true
}

if [ $# -gt 0 ]; then CMD="${1}"; shift; else CMD="deploy"; fi
OPT_MIGRATE=0
OPT_COLLECT=0
for arg in "$@"; do
  case "${arg}" in
    --migrate)       OPT_MIGRATE=1 ;;
    --collectstatic) OPT_COLLECT=1 ;;
    *)               printf '未知参数: %s\n\n' "${arg}" >&2; usage; exit 2 ;;
  esac
done

case "${CMD}" in
  deploy)            deploy ;;
  start)             start ;;
  reload)            reload ;;
  status)            status ;;
  stop)              stop ;;
  watch)             watch ;;
  help|-h|--help)    usage ;;
  *)                 printf '未知命令: %s\n\n' "${CMD}" >&2; usage; exit 2 ;;
esac
