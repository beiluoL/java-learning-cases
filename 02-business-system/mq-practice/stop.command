#!/bin/bash
# =====================================================================================
#  MQ 消息队列实战 · 一键停止（macOS 双击运行）
#
#  双击   → stop all（前端 → 后端 → 本脚本拉起的中间件）
#  命令行 → ./stop.command [all|backend|frontend|middleware] [-y] [--force] [-c]
#           --force  跳过优雅等待，直接 SIGKILL
#           -c       停止中间件前交互确认（默认不问）
#
#  安全原则：只停「本项目」的进程（PID 文件 + 主类签名 + 工作目录校验），
#            外部早已运行的 Redis / RabbitMQ 一律保留。
# =====================================================================================
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

PROJECT_NAME="MQ 消息队列实战"
BACKEND_PORT=${BACKEND_PORT:-8080}
FRONTEND_PORT=${FRONTEND_PORT:-5173}
BACKEND_SIG="mq-practice-backend.jar"
RUN_DIR="$SCRIPT_DIR/.run"
PID_BACKEND="$RUN_DIR/backend.pid"
PID_FRONTEND="$RUN_DIR/frontend.pid"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info(){ printf "${CYAN}[INFO]${NC} %s\n" "$*"; }
ok(){   printf "${GREEN}[ OK ]${NC} %s\n" "$*"; }
warn(){ printf "${YELLOW}[WARN]${NC} %s\n" "$*"; }
err(){  printf "${RED}[FAIL]${NC} %s\n" "$*"; }

TARGET="all"; NO_WAIT=0; FORCE=0; CONFIRM=0
for a in "$@"; do
  case "$a" in
    all|backend|frontend|middleware) TARGET="$a" ;;
    -y|--yes) : ;;
    --force|-f) FORCE=1 ;;
    -c|--confirm) CONFIRM=1 ;;
    --no-wait) NO_WAIT=1 ;;
    *) warn "未知参数: $a（已忽略）" ;;
  esac
done

port_open(){ lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
port_pid(){  lsof -iTCP:"$1" -sTCP:LISTEN -n -P -t 2>/dev/null | head -1; }
proc_cwd(){  lsof -a -p "$1" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -1; }
is_project_process(){
  local pid=$1 cwd
  [ -z "$pid" ] && return 1
  cwd="$(proc_cwd "$pid")"
  [ -z "$cwd" ] && return 1
  case "$cwd" in *mq-practice*) return 0 ;; esac
  return 1
}
kill_graceful(){
  local pid=$1
  if [ "$FORCE" = "1" ]; then kill -9 "$pid" 2>/dev/null; sleep 1; return 0; fi
  kill "$pid" 2>/dev/null
  local i=0
  while [ $i -lt 10 ] && kill -0 "$pid" 2>/dev/null; do sleep 0.5; i=$((i+1)); done
  kill -0 "$pid" 2>/dev/null && { warn "进程 $pid 未响应 SIGTERM，升级为 SIGKILL"; kill -9 "$pid" 2>/dev/null; sleep 1; }
}
pause_exit(){
  [ "$NO_WAIT" = "1" ] && return 0
  if [ -t 0 ]; then
    echo ""
    read -t 600 -n 1 -s -r -p "${1:-按任意键退出...}" 2>/dev/null || true
    echo ""
  fi
}

stop_frontend(){
  local stopped=0 pid
  if [ -f "$PID_FRONTEND" ]; then
    pid="$(cat "$PID_FRONTEND")"
    if kill -0 "$pid" 2>/dev/null; then info "停止前端进程 (PID $pid)..."; kill_graceful "$pid"; stopped=1; fi
    rm -f "$PID_FRONTEND"
  fi
  if port_open "$FRONTEND_PORT"; then
    pid="$(port_pid "$FRONTEND_PORT")"
    if is_project_process "$pid"; then info "停止占用前端端口的本项目进程 (PID $pid)..."; kill_graceful "$pid"; stopped=1
    else warn "端口 $FRONTEND_PORT 被其它项目占用 (PID ${pid:-?})，保留不动"; fi
  fi
  if [ $stopped -eq 0 ]; then ok "前端未运行（无需停止）"; return 0; fi
  if port_open "$FRONTEND_PORT"; then err "前端端口 $FRONTEND_PORT 仍被占用，请手动检查"; return 1; fi
  ok "前端已停止（端口 $FRONTEND_PORT 已释放）"
}

stop_backend(){
  local stopped=0 pid
  if [ -f "$PID_BACKEND" ]; then
    pid="$(cat "$PID_BACKEND")"
    if kill -0 "$pid" 2>/dev/null; then info "停止后端进程 (PID $pid)..."; kill_graceful "$pid"; stopped=1; fi
    rm -f "$PID_BACKEND"
  fi
  # 兜底：按主类签名精准识别（不是 pkill -f java）
  for pid in $(pgrep -f "$BACKEND_SIG" 2>/dev/null); do
    if is_project_process "$pid"; then info "停止残留后端进程 (PID $pid)..."; kill_graceful "$pid"; stopped=1; fi
  done
  if port_open "$BACKEND_PORT"; then
    pid="$(port_pid "$BACKEND_PORT")"
    if is_project_process "$pid"; then kill_graceful "$pid"; stopped=1
    else warn "端口 $BACKEND_PORT 被其它项目占用 (PID ${pid:-?})，保留不动"; fi
  fi
  if [ $stopped -eq 0 ]; then ok "后端未运行（无需停止）"; return 0; fi
  if port_open "$BACKEND_PORT"; then err "后端端口 $BACKEND_PORT 仍被占用，请手动检查"; return 1; fi
  ok "后端已停止（端口 $BACKEND_PORT 已释放）"
}

stop_middleware(){
  local name port mode_file
  for name in rabbitmq redis; do
    case "$name" in rabbitmq) port=5672 ;; *) port=6379 ;; esac
    mode_file="$RUN_DIR/mw_${name}.mode"
    if [ -f "$mode_file" ]; then
      if [ "$CONFIRM" = "1" ] && [ "$TARGET" = "middleware" -o "$TARGET" = "all" ]; then
        printf "确认停止本脚本拉起的 %s? [y/N]: " "$name"
        read -r ans
        case "$ans" in y|Y) : ;; *) warn "跳过 $name"; continue ;; esac
      fi
      case "$(cat "$mode_file")" in
        docker) info "停止 $name 容器..."; docker compose stop "$name" >/dev/null 2>&1 ;;
        brew)   info "停止 $name 服务..."; brew services stop "$name" >/dev/null 2>&1 ;;
      esac
      rm -f "$mode_file"
      if port_open "$port"; then warn "$name 端口 $port 仍被占用"; else ok "$name 已停止（端口 $port 已释放）"; fi
    else
      if port_open "$port"; then info "$name 此前已在运行（非本脚本启动），保留不动"; else info "$name 未运行"; fi
    fi
  done
}

echo ""
echo -e "${BOLD}══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  ${PROJECT_NAME} · 一键停止 (目标: ${TARGET})${NC}"
echo -e "${BOLD}══════════════════════════════════════════════════${NC}"

case "$TARGET" in
  frontend)   stop_frontend ;;
  backend)    stop_backend ;;
  middleware) stop_middleware ;;
  all|*)
    stop_frontend
    stop_backend
    stop_middleware
    ;;
esac

# 清理残留 PID 文件（避免下次启动被陈旧 PID 阻塞）
rm -f "$PID_BACKEND" "$PID_FRONTEND"

echo ""
echo -e "${BOLD}──────────────── 端口状态 ────────────────${NC}"
for p in "$BACKEND_PORT:后端" "$FRONTEND_PORT:前端" "5672:RabbitMQ" "6379:Redis"; do
  port="${p%%:*}"; label="${p##*:}"
  if port_open "$port"; then echo -e "  ${YELLOW}○${NC} ${label} (${port}) 仍在监听"; else echo -e "  ${GREEN}●${NC} ${label} (${port}) 已释放"; fi
done
echo -e "${BOLD}──────────────────────────────────────────${NC}"
echo ""

pause_exit "已停止，按任意键关闭此窗口..."
