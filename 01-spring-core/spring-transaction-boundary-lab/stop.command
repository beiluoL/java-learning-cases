#!/bin/bash
# ============================================================
#  spring-transaction-boundary-lab 一键停止 (macOS) · 后端 / 控制台 + 自有中间件
#  双击运行：默认 `stop all`（停止后端，并自动停止「本脚本拉起」的中间件）。
#  命令行： ./stop.command [all|backend|middleware] [-y] [--force] [-c]
#    target  : all(默认) | backend | middleware
#    -y      : 确认类提示统一回答 yes
#    --force : 跳过 SIGTERM 宽限，直接 SIGKILL（用于卡死进程）
#    -c      : 停止中间件前交互确认（默认不询问，保持一键式）
#  安全：仅停止本项目 PID / 主类签名 / 本脚本记录的中间件，绝不误杀其它项目。
# ============================================================
set -o pipefail

BACKEND_PORT=8095
BACKEND_SIG="TransactionLabApplication"
PROJECT_DIR="$(cd "$(dirname "$0")"; pwd)"
RUN_DIR="$PROJECT_DIR/.run"; PID_BACKEND="$RUN_DIR/backend.pid"
MW_PID_DIR="$RUN_DIR"
SERVICES_ENTRIES=()

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info(){ echo -e "${CYAN}[INFO]${NC} $*"; }
ok(){ echo -e "${GREEN}[ OK ]${NC} $*"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $*"; }
err(){ echo -e "${RED}[FAIL]${NC} $*"; }

TARGET="all"; YES=0; FORCE=0; CONFIRM=0
for a in "$@"; do
  case "$a" in
    all|backend|frontend|middleware) TARGET="$a" ;;
    -y|--yes) YES=1 ;;
    --force) FORCE=1 ;;
    -c|--confirm) CONFIRM=1 ;;
    *) warn "未知参数: $a（忽略）" ;;
  esac
done

mw_check_port(){ lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
mw_get_pid(){     lsof -iTCP:"$1" -sTCP:LISTEN -n -P -t 2>/dev/null; }

# 确认钩子：-c 且非 -y 时交互询问（默认 N）。双击/无 -c 默认直接执行（一键式）。
confirm(){
  [ "$YES" = "1" ] && return 0
  [ "$CONFIRM" = "0" ] && return 0
  local ans
  echo -e -n "${YELLOW}$* [y/N]: ${NC}"; read -r ans
  case "$ans" in y|Y) return 0 ;; *) return 1 ;; esac
}

# 优雅终止单个 PID（FORCE 时直接 SIGKILL）
kill_pid_graceful(){ local pid=$1; if [ "$FORCE" = "1" ]; then kill -9 "$pid" 2>/dev/null; return; fi; kill "$pid" 2>/dev/null; sleep 2; kill -0 "$pid" 2>/dev/null && { kill -9 "$pid" 2>/dev/null; sleep 1; }; }

stop_backend(){
  local stopped=0
  if [ -f "$PID_BACKEND" ]; then
    local pid; pid=$(cat "$PID_BACKEND")
    if kill -0 "$pid" 2>/dev/null; then info "停止后端进程 (PID $pid) ..."; kill_pid_graceful "$pid"; stopped=1; fi
    rm -f "$PID_BACKEND"
  fi
  local p; p=$(mw_get_pid "$BACKEND_PORT")
  if [ -n "$p" ]; then info "释放后端端口 $BACKEND_PORT (PID $p) ..."; kill_pid_graceful "$p"; stopped=1; fi
  # 兜底：按主类签名清理，但只处理真正以 java 启动的进程，绝不裸 pkill -f（会误伤 shell/grep）
  local q qcmd
  for q in $(pgrep -f "$BACKEND_SIG" 2>/dev/null); do
    qcmd=$(ps -o command= -p "$q" 2>/dev/null) || continue
    case "$qcmd" in *java*"$BACKEND_SIG"*) info "按主类签名清理残留 (PID $q) ..."; kill_pid_graceful "$q"; stopped=1 ;; esac
  done
  if [ $stopped -eq 0 ]; then ok "后端未运行（无需停止）"; return 0; fi
  p=$(mw_get_pid "$BACKEND_PORT")
  [ -n "$p" ] && { kill -9 "$p" 2>/dev/null; sleep 1; }
  if mw_check_port "$BACKEND_PORT"; then warn "后端端口 $BACKEND_PORT 仍被占用，请手动释放"; else ok "后端已停止（端口 $BACKEND_PORT 已释放）"; fi
}
mw_stop_all(){
  local leaked=0 entry s p c pidf pid
  if [ ${#SERVICES_ENTRIES[@]} -gt 0 ]; then
    for entry in "${SERVICES_ENTRIES[@]}"; do
      IFS='|' read -r s p c <<< "$entry"
      pidf="$MW_PID_DIR/mw_${s}.pid"
      if [ -f "$pidf" ]; then
        pid=$(cat "$pidf")
        echo ">>> 停止 $s (本脚本启动, PID: $pid, 端口 $p) ..."
        kill_pid_graceful "$pid"
        if mw_check_port "$p"; then mw_get_pid "$p" | xargs -r kill -9 2>/dev/null; sleep 1; fi
        if mw_check_port "$p"; then echo -e "${RED}[FAIL] $s 端口 $p 仍被占用，请手动检查${NC}"; leaked=1
        else echo -e "${GREEN}[ OK ] $s 已停止（端口 $p 已释放）${NC}"; fi
        rm -f "$pidf"
      else
        if mw_check_port "$p"; then echo -e "${CYAN}[INFO] $s 此前已在运行（非本脚本启动），未停止，请自行管理${NC}"
        else echo -e "${CYAN}[INFO] $s 未运行，无需停止${NC}"; fi
      fi
    done
  fi
  return $leaked
}

echo -e "${CYAN}===== 正在关闭 spring-transaction-boundary-lab 服务 (目标: $TARGET) =====${NC}"
if [ "$TARGET" = "backend" ] || [ "$TARGET" = "all" ]; then stop_backend; fi
if { [ "$TARGET" = "middleware" ] || [ "$TARGET" = "all" ]; } && [ "${#SERVICES_ENTRIES[@]}" -gt 0 ]; then
  if confirm "即将停止本脚本拉起的中间件（Redis/MySQL 等），确认?"; then mw_stop_all; else warn "已跳过中间件停止（按用户取消）"; fi
fi
echo -e "${GREEN}===== 关闭完成 =====${NC}"
read -n 1 -s -r -p "按任意键退出..."
