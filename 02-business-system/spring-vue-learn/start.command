#!/usr/bin/env bash
#
# Spring Vue Learn 一键启动 / 重启（macOS）
# 同时拉起：后端（Maven spring-boot:run :8080）与前端（Vite dev :5173）
# 双击：默认 start all（已运行则自动重启）。命令行： ./start.command [start|restart] [all|backend|frontend] [-y]
# 特性：依赖自检 / 端口-进程自校验 / 已运行则自动重启 / 清晰打印地址与状态 / 失败明确报错 / 窗口保持
#
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# ===== 配置 =====
PROJECT_NAME="Spring Vue Learn 学习框架"
BACKEND_PORT=8080
FRONTEND_PORT=5173
BACKEND_MAVEN_GOAL="spring-boot:run"
BACKEND_HEALTH_PATH="/api/health"
BACKEND_COMPILE_CMD="true"
MYSQL_SQL_PATH=""
WEBAPP_DIR="frontend"
BACKEND_HOST="127.0.0.1"
# 中间件联动启动（留空跳过）：每项 "服务名|端口|连接串"。MySQL 由用户自管，故留空。
SERVICES_ENTRIES=()

BACKEND_LOG="$SCRIPT_DIR/logs/backend.log"
FRONTEND_LOG="$SCRIPT_DIR/logs/frontend.log"
RUN_DIR="$SCRIPT_DIR/.run"
PID_BACKEND="$RUN_DIR/backend.pid"
PID_FRONTEND="$RUN_DIR/frontend.pid"
MW_PID_DIR="$RUN_DIR"
BACKEND_URL="http://localhost:${BACKEND_PORT}/"
HEALTH_URL="http://localhost:${BACKEND_PORT}${BACKEND_HEALTH_PATH}"
FRONTEND_URL="http://localhost:${FRONTEND_PORT}/"
MAX_WAIT=150

# ===== 颜色 / 状态 =====
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info(){ echo -e "${CYAN}[INFO]${NC} $*"; }
ok(){ echo -e "${GREEN}[ OK ]${NC} $*"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $*"; }
err(){ echo -e "${RED}[FAIL]${NC} $*"; }

# ===== 参数解析 =====
ACTION="start"; TARGET="all"; YES=0
for a in "$@"; do
  case "$a" in
    start|restart) ACTION="$a" ;;
    all|backend|frontend|middleware) TARGET="$a" ;;
    -y|--yes) YES=1 ;;
    *) warn "未知参数: $a（忽略）" ;;
  esac
done

# ===== 中间件联动（本例无，保留以便扩展）=====
MW_MAX_WAIT=15
mw_check_port(){ lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
mw_get_pid(){     lsof -iTCP:"$1" -sTCP:LISTEN -n -P -t 2>/dev/null; }
mw_start_one(){
  local name=$1 port=$2 conn=$3
  if mw_check_port "$port"; then echo -e "${GREEN}[SKIP] $name 已在运行 (地址: $conn)${NC}"; return 0; fi
  case $name in
    redis)    brew services start redis     2>/dev/null || redis-server --daemonize yes || { err "$name 启动失败"; return 1; } ;;
    mysql)    brew services start mysql     2>/dev/null || mysql.server start           || { err "$name 启动失败"; return 1; } ;;
    *) warn "未知服务 $name，跳过"; return 1 ;;
  esac
  local r=0
  while ! mw_check_port "$port" && [ $r -lt "$MW_MAX_WAIT" ]; do echo -n "."; sleep 1; r=$((r+1)); done
  echo ""
  if mw_check_port "$port"; then ok "$name 启动成功 (地址: $conn)"; else err "$name 启动超时"; return 1; fi
}
mw_start_all(){ local entry s p c; if [ ${#SERVICES_ENTRIES[@]} -gt 0 ]; then for entry in "${SERVICES_ENTRIES[@]}"; do IFS='|' read -r s p c <<< "$entry"; mw_start_one "$s" "$p" "$c" || { err "关键中间件 $s 启动失败，终止启动"; exit 1; }; done; fi; }
mw_stop_all(){
  local entry s p pidf pid
  if [ ${#SERVICES_ENTRIES[@]} -gt 0 ]; then
    for entry in "${SERVICES_ENTRIES[@]}"; do
      IFS='|' read -r s p c <<< "$entry"
      pidf="$MW_PID_DIR/mw_${s}.pid"
      if [ -f "$pidf" ]; then
        pid=$(cat "$pidf"); info "停止 $s (本脚本启动, PID: $pid) ..."; kill "$pid" 2>/dev/null; sleep 2
        if mw_check_port "$p"; then mw_get_pid "$p" | xargs -r kill -9 2>/dev/null; sleep 1; fi
        [ -f "$pidf" ] && rm -f "$pidf"; ok "$s 已停止"
      else
        mw_check_port "$p" && info "$s 非本脚本启动，未停止（请自行管理）" || info "$s 未运行"
      fi
    done
  fi
}
mw_print_status(){ local entry s p c; if [ ${#SERVICES_ENTRIES[@]} -gt 0 ]; then for entry in "${SERVICES_ENTRIES[@]}"; do IFS='|' read -r s p c <<< "$entry"; if mw_check_port "$p"; then echo -e "  - ${GREEN}$s: 运行中 ($c)${NC}"; else echo -e "  - ${RED}$s: 未运行${NC}"; fi; done; fi; }

# ===== 关停本项目的辅助函数 =====
kill_pid_graceful(){ local pid=$1; kill "$pid" 2>/dev/null; sleep 2; kill -0 "$pid" 2>/dev/null && { kill -9 "$pid" 2>/dev/null; sleep 1; }; }
stop_frontend(){
  local stopped=0
  if [ -f "$PID_FRONTEND" ]; then
    local pid; pid=$(cat "$PID_FRONTEND")
    if kill -0 "$pid" 2>/dev/null; then info "停止前端进程 (PID $pid) ..."; kill_pid_graceful "$pid"; stopped=1; fi
    rm -f "$PID_FRONTEND"
  fi
  if mw_check_port "$FRONTEND_PORT"; then mw_get_pid "$FRONTEND_PORT" | xargs -r kill_pid_graceful; stopped=1; fi
  [ $stopped -eq 0 ] && { ok "前端未运行（无需停止）"; return 0; }
  mw_check_port "$FRONTEND_PORT" && warn "前端端口 $FRONTEND_PORT 仍被占用，请手动释放" || ok "前端已停止（端口 $FRONTEND_PORT 已释放）"
}
stop_backend(){
  local stopped=0
  if [ -f "$PID_BACKEND" ]; then
    local pid; pid=$(cat "$PID_BACKEND")
    if kill -0 "$pid" 2>/dev/null; then info "停止后端进程 (PID $pid) ..."; kill_pid_graceful "$pid"; stopped=1; fi
    rm -f "$PID_BACKEND"
  fi
  if mw_check_port "$BACKEND_PORT"; then mw_get_pid "$BACKEND_PORT" | xargs -r kill_pid_graceful; stopped=1; fi
  [ $stopped -eq 0 ] && { ok "后端未运行（无需停止）"; return 0; }
  mw_check_port "$BACKEND_PORT" && warn "后端端口 $BACKEND_PORT 仍被占用，请手动释放" || ok "后端已停止（端口 $BACKEND_PORT 已释放）"
}

# ===== 1. 前置依赖检查 =====
info "检查运行环境（${PROJECT_NAME}）..."
if ! command -v java >/dev/null 2>&1; then err "未找到 java（JDK 17），请先安装 JDK 并加入 PATH。"; exit 1; fi
if ! command -v mvn  >/dev/null 2>&1; then err "未找到 mvn（Maven），请先安装并加入 PATH。"; exit 1; fi
if ! command -v curl >/dev/null 2>&1; then err "未找到 curl，请先安装 curl。"; exit 1; fi
ok "java / mvn / curl 就绪"

# ===== 2. 准备目录 =====
mkdir -p "$SCRIPT_DIR/logs" "$RUN_DIR"

# ===== 3. 编译（已设为 true，由 spring-boot:run 自行编译，跳过预编译）=====
compile_if_needed(){
  if [ "$BACKEND_COMPILE_CMD" != "true" ]; then
    info "编译项目（${BACKEND_COMPILE_CMD}）..."
    if ! eval "$BACKEND_COMPILE_CMD" > "$BACKEND_LOG" 2>&1; then
      err "编译失败，查看日志： tail -n 50 logs/backend.log"; exit 1
    fi
    ok "编译成功"
  fi
}

# ===== 4. 启动后端 =====
start_backend(){
  info "启动后端（Maven ${BACKEND_MAVEN_GOAL}）-> ${BACKEND_URL}"
  nohup mvn -B -f backend/pom.xml ${BACKEND_MAVEN_GOAL} >> "$BACKEND_LOG" 2>&1 &
  echo $! > "$PID_BACKEND"
  ok "后端进程已拉起 (PID $(cat "$PID_BACKEND"))"
  wait_backend(){
    local i=0
    while [ $i -lt "$MAX_WAIT" ]; do
      code=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null)
      if [ "$code" = "200" ] || [ "$code" = "302" ] || [ "$code" = "301" ]; then return 0; fi
      sleep 2; i=$((i+2))
    done
    return 1
  }
  info "等待后端就绪（最长 ${MAX_WAIT}s）..."
  if ! wait_backend; then
    err "后端启动超时（请确认 MySQL 已启动且账号密码正确）。最近日志："; tail -n 25 "$BACKEND_LOG"
    warn "正在清理已启动的进程..."; [ -f "$PID_BACKEND" ] && kill "$(cat "$PID_BACKEND")" 2>/dev/null; rm -f "$PID_BACKEND"; return 1
  fi
  ok "后端就绪：${BACKEND_URL}"
}

# ===== 5. 启动前端（Vite dev，首次自动 npm install）=====
start_frontend(){
  if mw_check_port "$FRONTEND_PORT"; then warn "端口 ${FRONTEND_PORT} 已被占用（可能已有前端在跑），跳过独立启动。页面仍可经后端访问。"; return 0; fi
  if ! command -v node >/dev/null 2>&1; then warn "未检测到 node，跳过前端启动（请手动 cd frontend && npm install && npm run dev）。"; return 0; fi
  if ! command -v npm  >/dev/null 2>&1; then warn "未检测到 npm，跳过前端启动。"; return 0; fi
  if [ ! -d "$SCRIPT_DIR/frontend/node_modules" ]; then
    info "首次运行：安装前端依赖（npm install）..."
    ( cd "$SCRIPT_DIR/frontend" && npm install ) >> "$FRONTEND_LOG" 2>&1 || { warn "前端依赖安装失败，请手动 cd frontend && npm install。日志： tail -n 20 logs/frontend.log"; return 0; }
  fi
  info "启动前端（Vite dev :$FRONTEND_PORT）-> $FRONTEND_URL"
  nohup bash -c "cd '$SCRIPT_DIR/frontend' && npm run dev" >> "$FRONTEND_LOG" 2>&1 &
  echo $! > "$PID_FRONTEND"
  i=0; code=""
  while [ $i -lt 30 ]; do
    code=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL" 2>/dev/null)
    [ "$code" = "200" ] && break
    sleep 1; i=$((i+1))
  done
  if [ "$code" = "200" ]; then ok "前端就绪：${FRONTEND_URL}"
  else warn "前端未就绪（可能仍在安装依赖/编译）。日志： tail -n 20 logs/frontend.log"; fi
}

# ===== 6. 主流程：按目标执行 =====
do_middleware(){ if [ "$ACTION" = "restart" ]; then mw_stop_all; fi; mw_start_all; }
do_backend(){
  if [ "$ACTION" = "restart" ]; then stop_backend; compile_if_needed; start_backend || exit 1; return; fi
  if mw_check_port "$BACKEND_PORT" || { [ -f "$PID_BACKEND" ] && kill -0 "$(cat "$PID_BACKEND")" 2>/dev/null; }; then
    warn "检测到后端已在运行，将先关闭再重启（自动重启）..."; stop_backend
  fi
  compile_if_needed; start_backend || exit 1
}
do_frontend(){
  if [ "$ACTION" = "restart" ]; then stop_frontend; start_frontend; return; fi
  start_frontend
}

echo -e "${BOLD}========== ${PROJECT_NAME} ${ACTION} (目标: $TARGET) ==========${NC}"
case "$TARGET" in
  backend)    do_backend ;;
  frontend)   do_frontend ;;
  middleware) do_middleware ;;
  all|*)      do_middleware; do_backend; do_frontend ;;
esac

# ===== 7. 状态总览 =====
echo ""
echo -e "${BOLD}================ ${PROJECT_NAME} 服务状态 ================${NC}"
if [ "$TARGET" = "backend" ] || [ "$TARGET" = "all" ]; then
  if mw_check_port "$BACKEND_PORT"; then echo -e "  ${GREEN}●${NC} 后端 (API)        : 运行中   ${BACKEND_URL}"; else echo -e "  ${RED}●${NC} 后端 (API)        : 未运行"; fi
fi
if [ "$TARGET" = "frontend" ] || [ "$TARGET" = "all" ]; then
  if mw_check_port "$FRONTEND_PORT"; then echo -e "  ${GREEN}●${NC} 前端 (预览)       : 运行中   ${FRONTEND_URL}"; else echo -e "  ${YELLOW}○${NC} 前端 (预览)       : 未独立启动（请检查日志 logs/frontend.log）"; fi
fi
echo -e "----------------------------------------------"
echo -e "  后端日志 : tail -f logs/backend.log"
echo -e "  前端日志 : tail -f logs/frontend.log"
echo -e "  一键关闭 : ./stop.command   (重启: ./restart.command)"
echo -e "${BOLD}==========================================${NC}"
echo ""
info "后端启动日志快照（末尾 12 行）："
tail -n 12 "$BACKEND_LOG" 2>/dev/null
echo ""
