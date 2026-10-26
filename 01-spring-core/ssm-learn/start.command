#!/usr/bin/env bash
#
# ssm-learn 一键启动（macOS：双击 start.command 或终端 `bash start.command` 均可）
# 同时拉起：后端（SSM + 嵌入式 Jetty :8080）与前端（Layui 预览 :8081，Node 零依赖）
# 特性：依赖自检 / 端口-进程自校验 / 已运行则自动重启 / 清晰打印地址 / 失败明确报错 / 窗口保持
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# ===== 配置 =====
BACKEND_PORT=8080
FRONTEND_PORT=8081
BACKEND_LOG="$SCRIPT_DIR/logs/backend.log"
FRONTEND_LOG="$SCRIPT_DIR/logs/frontend.log"
RUN_DIR="$SCRIPT_DIR/.run"
PID_BACKEND="$RUN_DIR/backend.pid"
PID_FRONTEND="$RUN_DIR/frontend.pid"
BACKEND_URL="http://localhost:${BACKEND_PORT}/"
FRONTEND_URL="http://localhost:${FRONTEND_PORT}/"
MAX_WAIT=150   # 后端就绪最长等待秒数

# ===== 颜色 =====
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info(){ echo -e "${CYAN}[INFO]${NC} $*"; }
ok(){ echo -e "${GREEN}[ OK ]${NC} $*"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $*"; }
err(){ echo -e "${RED}[FAIL]${NC} $*"; }

# ===== 关停本项目的辅助函数（供“自动重启”复用）=====
stop_service(){
  [ -f "$PID_FRONTEND" ] && kill "$(cat "$PID_FRONTEND")" 2>/dev/null && echo -e "  ${CYAN}·${NC} 前端进程 (PID $(cat "$PID_FRONTEND")) 已发送终止信号"
  [ -f "$PID_BACKEND" ]  && kill "$(cat "$PID_BACKEND")"  2>/dev/null && echo -e "  ${CYAN}·${NC} 后端进程 (PID $(cat "$PID_BACKEND")) 已发送终止信号"
  sleep 2
  for p in "$FRONTEND_PORT" "$BACKEND_PORT"; do
    if lsof -iTCP:"$p" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
      lsof -iTCP:"$p" -sTCP:LISTEN -n -P -t | xargs -r kill 2>/dev/null
      sleep 1
      lsof -iTCP:"$p" -sTCP:LISTEN -n -P >/dev/null 2>&1 && lsof -iTCP:"$p" -sTCP:LISTEN -n -P -t | xargs -r kill -9 2>/dev/null
    fi
  done
  rm -f "$PID_BACKEND" "$PID_FRONTEND"
}

# ===== 1. 前置依赖检查 =====
info "检查运行环境..."
if ! command -v java >/dev/null 2>&1; then err "未找到 java（JDK 8+），请先安装 JDK 并加入 PATH。"; exit 1; fi
if ! command -v mvn  >/dev/null 2>&1; then err "未找到 mvn（Maven），请先安装并加入 PATH。"; exit 1; fi
if ! command -v curl >/dev/null 2>&1; then err "未找到 curl，请先安装 curl。"; exit 1; fi
ok "java / mvn / curl 就绪"

# ===== 2. 准备目录 =====
mkdir -p "$SCRIPT_DIR/logs" "$RUN_DIR"

# ===== 3. 端口/进程自校验 + 自动重启 =====
port_listener(){ lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
if port_listener "$BACKEND_PORT" || { [ -f "$PID_BACKEND" ] && kill -0 "$(cat "$PID_BACKEND")" 2>/dev/null; }; then
  warn "检测到后端已在运行，将先关闭再重启（自动重启）..."
  stop_service
fi
if port_listener "$FRONTEND_PORT"; then
  warn "端口 ${FRONTEND_PORT} 已被占用，将跳过独立前端预览服务（仍可用后端地址访问页面）。"
  SKIP_FRONTEND=1
fi

# ===== 4. MySQL 检查（仅警告，不阻断：Druid 已惰性初始化）=====
if command -v mysqladmin >/dev/null 2>&1; then
  if mysqladmin ping >/dev/null 2>&1; then ok "MySQL 可连通"; else warn "MySQL 未启动或不可达：应用可启动，但涉及数据库的功能会报错。请先启动 MySQL 并执行 sql/ssm_learn.sql"; fi
else
  warn "未检测到 mysqladmin，跳过 MySQL 检查。请确保 MySQL 已启动并执行过 sql/ssm_learn.sql"
fi

# ===== 5. 编译（失败快速暴露，不进入启动）=====
info "编译项目（mvn compile）..."
if ! mvn -B -q compile > "$BACKEND_LOG" 2>&1; then
  err "Maven 编译失败，查看日志： tail -n 50 logs/backend.log"
  exit 1
fi
ok "编译成功"

# ===== 6. 启动后端（SSM + Jetty）=====
info "启动后端（SSM + 嵌入式 Jetty）-> ${BACKEND_URL}"
nohup mvn -B jetty:run >> "$BACKEND_LOG" 2>&1 &
echo $! > "$PID_BACKEND"
ok "后端进程已拉起 (PID $(cat "$PID_BACKEND"))"

wait_backend(){
  local i=0
  while [ $i -lt "$MAX_WAIT" ]; do
    code=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_URL" 2>/dev/null)
    if [ "$code" = "200" ] || [ "$code" = "302" ] || [ "$code" = "301" ]; then return 0; fi
    sleep 2; i=$((i+2))
  done
  return 1
}
info "等待后端就绪（最长 ${MAX_WAIT}s）..."
if ! wait_backend; then
  err "后端启动超时。最近日志："; tail -n 25 "$BACKEND_LOG"
  warn "正在清理已启动的进程..."
  [ -f "$PID_BACKEND" ] && kill "$(cat "$PID_BACKEND")" 2>/dev/null
  exit 1
fi
ok "后端就绪：${BACKEND_URL}"

# ===== 7. 启动前端（Layui 预览，Node 零依赖，API 反代到后端）=====
if [ -z "${SKIP_FRONTEND:-}" ] && command -v node >/dev/null 2>&1; then
  info "启动前端（Layui 预览服务）-> ${FRONTEND_URL}"
  nohup node "$SCRIPT_DIR/scripts/frontend-dev-server.js" >> "$FRONTEND_LOG" 2>&1 &
  echo $! > "$PID_FRONTEND"
  i=0; code=""
  while [ $i -lt 30 ]; do
    code=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL" 2>/dev/null)
    [ "$code" = "200" ] && break
    sleep 1; i=$((i+1))
  done
  if [ "$code" = "200" ]; then
    ok "前端就绪：${FRONTEND_URL}"
  else
    warn "前端预览服务启动异常（不影响后端，页面仍可经后端地址访问）。日志： tail -n 20 logs/frontend.log"
    rm -f "$PID_FRONTEND"
  fi
elif [ -n "${SKIP_FRONTEND:-}" ]; then
  info "已跳过独立前端预览服务（端口占用）。页面经后端访问：${BACKEND_URL}"
else
  warn "未检测到 node，跳过独立前端预览服务。页面经后端访问：${BACKEND_URL}"
fi

# ===== 8. 状态总览 =====
echo ""
echo -e "${BOLD}================ 服务状态 ================${NC}"
echo -e "  ${GREEN}●${NC} 后端 (SSM API)    : 运行中   ${BACKEND_URL}"
if [ -f "$PID_FRONTEND" ]; then
  echo -e "  ${GREEN}●${NC} 前端 (Layui 预览) : 运行中   ${FRONTEND_URL}  (Node 静态服务 + API 反代)"
else
  echo -e "  ${YELLOW}○${NC} 前端 (Layui 预览) : 未独立启动（页面经后端访问）"
fi
echo -e "----------------------------------------------"
echo -e "  后端日志 : tail -f logs/backend.log"
echo -e "  前端日志 : tail -f logs/frontend.log"
echo -e "  一键关闭 : ./stop.command"
echo -e "${BOLD}==========================================${NC}"
echo ""
info "后端启动日志快照（末尾 12 行）："
tail -n 12 "$BACKEND_LOG"
echo ""
