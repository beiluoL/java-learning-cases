#!/bin/bash
# =====================================================================================
#  MQ 消息队列实战 · 一键启动 / 重启（macOS 双击运行）
#
#  双击        → start all（后端 + 前端 + 中间件；已运行则自动「先停后起」）
#  命令行      → ./start.command [start|restart] [all|backend|frontend|middleware] [-y]
#  端口覆盖    → BACKEND_PORT=8081 FRONTEND_PORT=5174 ./start.command
#
#  中间件策略：
#    RabbitMQ / Redis 已在运行 → 直接复用（不重复拉起、不写入归属标记）
#    未运行且由本脚本拉起     → 记录 .run/mw_<名>.mode，stop 时只停「自己拉起的」
# =====================================================================================
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

PROJECT_NAME="MQ 消息队列实战"
BACKEND_PORT=${BACKEND_PORT:-8080}
FRONTEND_PORT=${FRONTEND_PORT:-5173}
BACKEND_SIG="mq-practice-backend.jar"
HEALTH_PATH="/api/mq/queues"
JAR_PATH="backend/mq-practice-backend/target/mq-practice-backend.jar"
RUN_DIR="$SCRIPT_DIR/.run"
LOG_DIR="$SCRIPT_DIR/logs"
PID_BACKEND="$RUN_DIR/backend.pid"
PID_FRONTEND="$RUN_DIR/frontend.pid"
MAX_WAIT=180

# ------------------------------------------------------------------ JDK 选择
# 本机 Maven 可能默认跑在很新的 JDK 上（如 JDK 26），而 Spring Boot 3.4 / Lombok
# 并不支持那么新的版本，表现为「Lombok 生成的 getter 凭空消失」这类诡异编译错误。
# 这里显式挑选 17/21 的 JDK 并导出 JAVA_HOME，保证 mvn 与 java 用同一个。
pick_jdk() {
  local v home
  if [ -x /usr/libexec/java_home ]; then
    for v in 17 21 23; do
      home="$(/usr/libexec/java_home -v "$v" 2>/dev/null)"
      if [ -n "$home" ] && [ -x "$home/bin/java" ]; then
        echo "$home"
        return 0
      fi
    done
  fi
  return 1
}
PICKED_JDK="$(pick_jdk || true)"
if [ -n "$PICKED_JDK" ]; then
  export JAVA_HOME="$PICKED_JDK"
fi
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "$JAVA_BIN" ] || JAVA_BIN="java"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info(){ printf "${CYAN}[INFO]${NC} %s\n" "$*"; }
ok(){   printf "${GREEN}[ OK ]${NC} %s\n" "$*"; }
warn(){ printf "${YELLOW}[WARN]${NC} %s\n" "$*"; }
err(){  printf "${RED}[FAIL]${NC} %s\n" "$*"; }
skip(){ printf "${GREEN}[SKIP]${NC} %s\n" "$*"; }

ACTION="start"; TARGET="all"; NO_WAIT=0
for a in "$@"; do
  case "$a" in
    start|restart) ACTION="$a" ;;
    all|backend|frontend|middleware) TARGET="$a" ;;
    -y|--yes) : ;;
    --no-wait) NO_WAIT=1 ;;
    *) warn "未知参数: $a（已忽略）" ;;
  esac
done

mkdir -p "$RUN_DIR" "$LOG_DIR"

# ------------------------------------------------------------------ 基础探测
port_open(){ lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
port_pid(){  lsof -iTCP:"$1" -sTCP:LISTEN -n -P -t 2>/dev/null | head -1; }
proc_cwd(){  lsof -a -p "$1" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -1; }
# 归属校验：lsof 会把中文路径转义成 \xNN，所以用 ASCII 目录名匹配
is_project_process(){
  local pid=$1 cwd
  [ -z "$pid" ] && return 1
  cwd="$(proc_cwd "$pid")"
  [ -z "$cwd" ] && return 1
  case "$cwd" in *mq-practice*) return 0 ;; esac
  return 1
}
http_code_local(){
  local port=$1 path=$2 code
  code=$(curl -s -g --noproxy '*' -o /dev/null -w "%{http_code}" -m 3 "http://127.0.0.1:${port}${path}" 2>/dev/null)
  [ "$code" != "200" ] && code=$(curl -s -g --noproxy '*' -o /dev/null -w "%{http_code}" -m 3 "http://[::1]:${port}${path}" 2>/dev/null)
  printf '%s' "$code"
}
kill_graceful(){
  local pid=$1
  kill "$pid" 2>/dev/null
  local i=0
  while [ $i -lt 10 ] && kill -0 "$pid" 2>/dev/null; do sleep 0.5; i=$((i+1)); done
  kill -0 "$pid" 2>/dev/null && { kill -9 "$pid" 2>/dev/null; sleep 1; }
}
pause_exit(){
  [ "$NO_WAIT" = "1" ] && return 0
  if [ -t 0 ]; then
    echo ""
    read -t 600 -n 1 -s -r -p "${1:-按任意键退出...}" 2>/dev/null || true
    echo ""
  fi
}

# ------------------------------------------------------------------ 中间件
mw_marker(){ echo "$RUN_DIR/mw_$1.mode"; }

mw_wait_port(){
  local port=$1 i=0
  while [ $i -lt 40 ]; do
    port_open "$port" && return 0
    sleep 1; i=$((i+1))
  done
  return 1
}

mw_start_rabbitmq(){
  echo ">>> RabbitMQ (5672)"
  if port_open 5672; then skip "RabbitMQ 已在运行（外部进程，本脚本不会停止它）"; return 0; fi
  if ! command -v docker >/dev/null 2>&1; then
    err "RabbitMQ 未运行，且本机没有 docker。请安装 Docker Desktop 或 brew install rabbitmq"
    return 1
  fi
  if ! docker info >/dev/null 2>&1; then
    err "Docker 守护进程未启动，请先打开 Docker Desktop 再试"
    return 1
  fi
  info "拉起 RabbitMQ 容器（docker compose up -d rabbitmq）..."
  docker compose up -d rabbitmq > "$LOG_DIR/middleware.log" 2>&1
  if ! mw_wait_port 5672; then
    err "RabbitMQ 启动超时，查看日志：logs/middleware.log"
    return 1
  fi
  echo "docker" > "$(mw_marker rabbitmq)"
  ok "RabbitMQ 就绪：amqp://localhost:5672  控制台 http://localhost:15672 (guest/guest)"
}

mw_start_redis(){
  echo ">>> Redis (6379)"
  if port_open 6379; then skip "Redis 已在运行（外部进程，本脚本不会停止它）"; return 0; fi
  if command -v brew >/dev/null 2>&1 && brew list redis >/dev/null 2>&1; then
    info "拉起本地 Redis（brew services start redis）..."
    brew services start redis >/dev/null 2>&1
    if mw_wait_port 6379; then echo "brew" > "$(mw_marker redis)"; ok "Redis 就绪：redis://localhost:6379"; return 0; fi
  fi
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    info "拉起 Redis 容器（docker compose up -d redis）..."
    REDIS_HOST_PORT=6379 docker compose up -d redis > "$LOG_DIR/middleware.log" 2>&1
    if mw_wait_port 6379; then echo "docker" > "$(mw_marker redis)"; ok "Redis 就绪：redis://localhost:6379"; return 0; fi
  fi
  err "Redis 启动失败。请安装：brew install redis 或 docker compose up -d redis"
  return 1
}

mw_stop_one(){
  local name=$1 port=$2 marker
  marker="$(mw_marker "$name")"
  if [ -f "$marker" ]; then
    case "$(cat "$marker")" in
      docker)
        info "停止本脚本拉起的 $name 容器..."
        docker compose stop "$name" >/dev/null 2>&1
        ;;
      brew)
        info "停止本脚本拉起的 $name 服务..."
        brew services stop "$name" >/dev/null 2>&1
        ;;
    esac
    rm -f "$marker"
    if port_open "$port"; then warn "$name 端口 $port 仍被占用，请手动检查"; else ok "$name 已停止（端口 $port 已释放）"; fi
  else
    if port_open "$port"; then info "$name 此前已在运行（非本脚本启动），保留不动"; else info "$name 未运行"; fi
  fi
}

# ------------------------------------------------------------------ 后端
backend_healthy(){ [ "$(http_code_local "$BACKEND_PORT" "$HEALTH_PATH")" = "200" ]; }

stop_backend(){
  local pid stopped=0
  if [ -f "$PID_BACKEND" ]; then
    pid="$(cat "$PID_BACKEND")"
    if kill -0 "$pid" 2>/dev/null; then
      info "停止后端进程 (PID $pid)..."
      kill_graceful "$pid"
      stopped=1
    fi
    rm -f "$PID_BACKEND"
  fi
  # 兜底：按主类签名精准识别（绝不 pkill -f java）
  for pid in $(pgrep -f "$BACKEND_SIG" 2>/dev/null); do
    if is_project_process "$pid"; then info "停止残留后端进程 (PID $pid)..."; kill_graceful "$pid"; stopped=1; fi
  done
  if port_open "$BACKEND_PORT"; then
    pid="$(port_pid "$BACKEND_PORT")"
    if is_project_process "$pid"; then kill_graceful "$pid"; stopped=1
    else warn "端口 $BACKEND_PORT 被其它进程占用 (PID ${pid:-?})，不终止它"; fi
  fi
  [ $stopped -eq 0 ] && { ok "后端未运行（无需停止）"; return 0; }
  if port_open "$BACKEND_PORT"; then warn "后端端口 $BACKEND_PORT 仍被占用"; else ok "后端已停止（端口 $BACKEND_PORT 已释放）"; fi
}

build_backend(){
  info "编译打包后端（Maven，多模块）..."
  if ! mvn -B -q -DskipTests package -f backend/pom.xml > "$LOG_DIR/backend-build.log" 2>&1; then
    err "后端编译失败，最后 25 行日志："
    tail -n 25 "$LOG_DIR/backend-build.log"
    return 1
  fi
  if [ ! -f "$JAR_PATH" ]; then err "未找到产物 $JAR_PATH"; return 1; fi
  ok "编译成功：$JAR_PATH"
}

start_backend(){
  if port_open "$BACKEND_PORT"; then
    local pid; pid="$(port_pid "$BACKEND_PORT")"
    if is_project_process "$pid"; then
      warn "后端已在运行，按「自动重启」处理"; stop_backend
    else
      err "端口 $BACKEND_PORT 已被其它进程占用 (PID ${pid:-?})"
      err "请释放端口，或换端口启动：BACKEND_PORT=8081 ./start.command"
      return 1
    fi
  fi
  build_backend || return 1

  info "启动后端 -> http://localhost:${BACKEND_PORT}"
  nohup "$JAVA_BIN" -jar "$JAR_PATH" --server.port="$BACKEND_PORT" > "$LOG_DIR/backend.log" 2>&1 &
  echo $! > "$PID_BACKEND"

  local i=0
  printf "     等待健康检查 "
  while [ $i -lt "$MAX_WAIT" ]; do
    if backend_healthy; then
      echo ""
      ok "后端就绪：http://localhost:${BACKEND_PORT}  (API 文档 /swagger-ui.html)"
      return 0
    fi
    if ! kill -0 "$(cat "$PID_BACKEND")" 2>/dev/null; then
      echo ""
      err "后端进程已退出，最后 25 行日志："
      tail -n 25 "$LOG_DIR/backend.log"
      rm -f "$PID_BACKEND"
      return 1
    fi
    printf "."; sleep 2; i=$((i+2))
  done
  echo ""
  err "后端启动超时（${MAX_WAIT}s），最后 25 行日志："
  tail -n 25 "$LOG_DIR/backend.log"
  kill_graceful "$(cat "$PID_BACKEND" 2>/dev/null)"
  rm -f "$PID_BACKEND"
  return 1
}

# ------------------------------------------------------------------ 前端
stop_frontend(){
  local pid stopped=0
  if [ -f "$PID_FRONTEND" ]; then
    pid="$(cat "$PID_FRONTEND")"
    if kill -0 "$pid" 2>/dev/null; then
      info "停止前端进程 (PID $pid)..."
      kill_graceful "$pid"
      stopped=1
    fi
    rm -f "$PID_FRONTEND"
  fi
  if port_open "$FRONTEND_PORT"; then
    pid="$(port_pid "$FRONTEND_PORT")"
    if is_project_process "$pid"; then kill_graceful "$pid"; stopped=1
    else warn "端口 $FRONTEND_PORT 被其它进程占用 (PID ${pid:-?})，不终止它"; fi
  fi
  [ $stopped -eq 0 ] && { ok "前端未运行（无需停止）"; return 0; }
  if port_open "$FRONTEND_PORT"; then warn "前端端口 $FRONTEND_PORT 仍被占用"; else ok "前端已停止（端口 $FRONTEND_PORT 已释放）"; fi
}

start_frontend(){
  if port_open "$FRONTEND_PORT"; then
    local pid; pid="$(port_pid "$FRONTEND_PORT")"
    if is_project_process "$pid"; then
      warn "前端已在运行，按「自动重启」处理"; stop_frontend
    else
      err "端口 $FRONTEND_PORT 已被其它进程占用 (PID ${pid:-?})，跳过前端"
      err "可换端口启动：FRONTEND_PORT=5174 ./start.command"
      return 1
    fi
  fi
  if ! command -v node >/dev/null 2>&1; then
    err "未检测到 node，无法启动前端。请安装 Node 18+"
    return 1
  fi
  if [ ! -d frontend/node_modules ]; then
    info "首次运行，安装前端依赖（npm install，可能需要几分钟）..."
    if ! (cd frontend && npm install --no-fund --no-audit > "$LOG_DIR/frontend-install.log" 2>&1); then
      err "npm install 失败，日志：logs/frontend-install.log"
      return 1
    fi
    ok "前端依赖安装完成"
  fi

  info "启动前端（Vite）-> http://localhost:${FRONTEND_PORT}"
  # 直接执行 vite 的入口脚本，保证 PID 就是 node 进程本身，stop 时能精准结束
  (
    cd frontend || exit 1
    VITE_API_TARGET="http://localhost:${BACKEND_PORT}" \
      nohup node node_modules/vite/bin/vite.js --port "$FRONTEND_PORT" --host > "$LOG_DIR/frontend.log" 2>&1 &
    echo $! > "$PID_FRONTEND"
  )
  local i=0 code=""
  printf "     等待前端就绪 "
  while [ $i -lt 60 ]; do
    code="$(http_code_local "$FRONTEND_PORT" "/")"
    [ "$code" = "200" ] && break
    printf "."; sleep 1; i=$((i+1))
  done
  echo ""
  if [ "$code" = "200" ]; then
    ok "前端就绪：http://localhost:${FRONTEND_PORT}"
    return 0
  fi
  err "前端启动失败，最后 20 行日志："
  tail -n 20 "$LOG_DIR/frontend.log"
  rm -f "$PID_FRONTEND"
  return 1
}

# ------------------------------------------------------------------ 主流程
echo ""
echo -e "${BOLD}══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  ${PROJECT_NAME} · ${ACTION} (目标: ${TARGET})${NC}"
echo -e "${BOLD}══════════════════════════════════════════════════${NC}"

# ------------------------------------------------------------------ 依赖自检
# 必须放在这里而不是脚本开头：mw_* 函数在定义阶段不应产生输出
if [ "$TARGET" != "frontend" ]; then
  if ! command -v mvn >/dev/null 2>&1; then
    err "未找到 mvn（Maven），请先安装并加入 PATH"
    pause_exit; exit 1
  fi
  if ! [ -x "$JAVA_BIN" ]; then
    err "未找到可用的 java（需要 JDK 17+），请安装后重试"
    pause_exit; exit 1
  fi
  info "JDK : $("$JAVA_BIN" -version 2>&1 | head -1)"
fi

case "$TARGET" in
  middleware)
    if [ "$ACTION" = "restart" ]; then mw_stop_one rabbitmq 5672; mw_stop_one redis 6379; fi
    mw_start_rabbitmq || { pause_exit; exit 1; }
    mw_start_redis || { pause_exit; exit 1; }
    ;;
  backend)
    if [ "$ACTION" = "restart" ]; then stop_backend; fi
    start_backend || { pause_exit; exit 1; }
    ;;
  frontend)
    if [ "$ACTION" = "restart" ]; then stop_frontend; fi
    start_frontend || { pause_exit; exit 1; }
    ;;
  all|*)
    if [ "$ACTION" = "restart" ]; then
      stop_frontend
      stop_backend
      mw_stop_one rabbitmq 5672
      mw_stop_one redis 6379
    fi
    mw_start_rabbitmq || { pause_exit; exit 1; }
    mw_start_redis || { pause_exit; exit 1; }
    start_backend || { pause_exit; exit 1; }
    start_frontend || { pause_exit; exit 1; }
    ;;
esac

# ------------------------------------------------------------------ 状态汇总
echo ""
echo -e "${BOLD}──────────────── 服务状态 ────────────────${NC}"
if port_open "$BACKEND_PORT"; then
  echo -e "  ${GREEN}●${NC} 后端 API     : 运行中   http://localhost:${BACKEND_PORT}"
  echo -e "                 Swagger   http://localhost:${BACKEND_PORT}/swagger-ui.html"
else
  echo -e "  ${RED}●${NC} 后端 API     : 未运行"
fi
if port_open "$FRONTEND_PORT"; then
  echo -e "  ${GREEN}●${NC} 前端控制台   : 运行中   http://localhost:${FRONTEND_PORT}"
else
  echo -e "  ${RED}●${NC} 前端控制台   : 未运行"
fi
if port_open 5672; then
  echo -e "  ${GREEN}●${NC} RabbitMQ     : 运行中   http://localhost:15672  (guest/guest)"
else
  echo -e "  ${RED}●${NC} RabbitMQ     : 未运行"
fi
if port_open 6379; then
  echo -e "  ${GREEN}●${NC} Redis        : 运行中   redis://localhost:6379"
else
  echo -e "  ${RED}●${NC} Redis        : 未运行"
fi
echo -e "${BOLD}──────────────────────────────────────────${NC}"
echo -e "  日志 : tail -f logs/backend.log   |   tail -f logs/frontend.log"
echo -e "  重启 : ./restart.command   关闭 : ./stop.command"
echo -e "${BOLD}──────────────────────────────────────────${NC}"
echo ""

pause_exit "启动流程结束，按任意键关闭此窗口..."
