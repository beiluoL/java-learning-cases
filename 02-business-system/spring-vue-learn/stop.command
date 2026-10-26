#!/usr/bin/env bash
#
# Spring Vue Learn 一键关闭（macOS）
# 精准关停本项目后端（:8080）与前端（:5173）。无误杀、幂等。
# 双击：默认 stop all。命令行： ./stop.command [all|backend|frontend] [-y] [--force]
#   -y      确认类提示统一 yes
#   --force 跳过 SIGTERM 宽限直接 SIGKILL（卡死进程）
#
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

BACKEND_PORT=8080
FRONTEND_PORT=5173
RUN_DIR="$SCRIPT_DIR/.run"
PID_BACKEND="$RUN_DIR/backend.pid"
PID_FRONTEND="$RUN_DIR/frontend.pid"
MW_PID_DIR="$RUN_DIR"
SERVICES_ENTRIES=()

CYAN='\033[0;36m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info(){ echo -e "${CYAN}[INFO]${NC} $*"; }
ok(){ echo -e "${GREEN}[ OK ]${NC} $*"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $*"; }
err(){ echo -e "${RED}[FAIL]${NC} $*"; }

TARGET="all"; YES=0; FORCE=0
for a in "$@"; do
  case "$a" in
    all|backend|frontend|middleware) TARGET="$a" ;;
    -y|--yes) YES=1 ;;
    --force) FORCE=1 ;;
    *) warn "未知参数: $a（忽略）" ;;
  esac
done

mw_check_port(){ lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
mw_get_pid(){     lsof -iTCP:"$1" -sTCP:LISTEN -n -P -t 2>/dev/null; }
kill_pid_graceful(){ local pid=$1; if [ "$FORCE" = "1" ]; then kill -9 "$pid" 2>/dev/null; return; fi; kill "$pid" 2>/dev/null; sleep 2; kill -0 "$pid" 2>/dev/null && { kill -9 "$pid" 2>/dev/null; sleep 1; }; }

stop_one(){
  local name=$1 pidfile=$2 port=$3
  local stopped=0
  if [ -f "$pidfile" ]; then
    local pid; pid=$(cat "$pidfile")
    if kill -0 "$pid" 2>/dev/null; then info "停止 $name 进程 (PID $pid) ..."; kill_pid_graceful "$pid"; stopped=1; fi
    rm -f "$pidfile"
  fi
  if mw_check_port "$port"; then mw_get_pid "$port" | xargs -r kill_pid_graceful; stopped=1; fi
  [ $stopped -eq 0 ] && { ok "$name 未运行（无需停止）"; return 0; }
  mw_check_port "$port" && warn "$name 端口 $port 仍被占用，请手动释放" || ok "$name 已停止（端口 $port 已释放）"
}
mw_stop_all(){
  local entry s p pidf pid
  if [ ${#SERVICES_ENTRIES[@]} -gt 0 ]; then
    for entry in "${SERVICES_ENTRIES[@]}"; do
      IFS='|' read -r s p c <<< "$entry"
      pidf="$MW_PID_DIR/mw_${s}.pid"
      if [ -f "$pidf" ]; then
        pid=$(cat "$pidf"); info "停止 $s (本脚本启动, PID: $pid, 端口 $p) ..."; kill_pid_graceful "$pid"
        if mw_check_port "$p"; then mw_get_pid "$p" | xargs -r kill -9 2>/dev/null; sleep 1; fi
        [ -f "$pidf" ] && rm -f "$pidf"; ok "$s 已停止（端口 $p 已释放）"
      else
        mw_check_port "$p" && info "$s 此前已在运行（非本脚本启动），未停止，请自行管理" || info "$s 未运行，无需停止"
      fi
    done
  fi
}

echo -e "${CYAN}===== 正在关闭 ${PROJECT_NAME:-Spring Vue Learn} 服务 (目标: $TARGET) =====${NC}"
if [ "$TARGET" = "backend" ]   || [ "$TARGET" = "all" ]; then stop_one "后端"  "$PID_BACKEND"   "$BACKEND_PORT"; fi
if [ "$TARGET" = "frontend" ]  || [ "$TARGET" = "all" ]; then stop_one "前端"  "$PID_FRONTEND" "$FRONTEND_PORT"; fi
if { [ "$TARGET" = "middleware" ] || [ "$TARGET" = "all" ]; } && [ "${#SERVICES_ENTRIES[@]}" -gt 0 ]; then
  mw_stop_all
fi
echo -e "${GREEN}===== 关闭完成 =====${NC}"
read -n 1 -s -r -p "按任意键退出..."
