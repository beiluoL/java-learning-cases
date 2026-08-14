#!/usr/bin/env bash
#
# ssm-learn 一键关闭脚本
# 精准关停后端（Jetty :8080）与前端（Layui 预览 :8081）服务。
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# ===== 配置（需与 start.sh 保持一致）=====
BACKEND_PORT=8080
FRONTEND_PORT=8081

RUN_DIR="$SCRIPT_DIR/.run"
PID_BACKEND="$RUN_DIR/backend.pid"
PID_FRONTEND="$RUN_DIR/frontend.pid"

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
info(){ echo -e "${CYAN}[INFO]${NC} $*"; }
ok(){ echo -e "${GREEN}[ OK ]${NC} $*"; }
err(){ echo -e "${RED}[FAIL]${NC} $*"; }

# 按端口杀进程（macOS 优先用 lsof）
kill_port(){
  local p=$1
  if ! lsof -iTCP:"$p" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
    echo -e "  端口 ${p}：无运行中的服务"
    return 0
  fi
  echo -e "  端口 ${p}：发现监听进程，正在终止..."
  lsof -iTCP:"$p" -sTCP:LISTEN -n -P -t | xargs -r kill 2>/dev/null
  sleep 1
  if lsof -iTCP:"$p" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
    echo -e "    仍未退出，强制 kill -9..."
    lsof -iTCP:"$p" -sTCP:LISTEN -n -P -t | xargs -r kill -9 2>/dev/null
    sleep 1
  fi
  if lsof -iTCP:"$p" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
    err "    端口 ${p} 仍然被占用，请手动检查并释放。"
  else
    ok "    端口 ${p} 已释放"
  fi
}

echo -e "${CYAN}===== 正在关闭 ssm-learn 服务 =====${NC}"
# 先按 PID 文件优雅终止（若存在且存活）
[ -f "$PID_FRONTEND" ] && kill "$(cat "$PID_FRONTEND")" 2>/dev/null && echo "  前端进程 (PID $(cat "$PID_FRONTEND")) 已发送终止信号"
[ -f "$PID_BACKEND" ]  && kill "$(cat "$PID_BACKEND")"  2>/dev/null && echo "  后端进程 (PID $(cat "$PID_BACKEND")) 已发送终止信号"
sleep 2

# 再按端口兜底（覆盖脚本外启动或 PID 失效的情况）
kill_port "$FRONTEND_PORT"
kill_port "$BACKEND_PORT"

# 清理 PID 文件
rm -f "$PID_BACKEND" "$PID_FRONTEND"
echo -e "${GREEN}===== 关闭完成 =====${NC}"
