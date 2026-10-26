#!/bin/bash
# ============================================================
#  spring-transaction-boundary-lab 一键启动 / 重启 (macOS) · 后端 / 控制台程序
#  双击运行：默认 `start all`（若后端已在运行则自动重启）。
#  命令行： ./start.command [start|restart] [all|backend|middleware] [-y]
#    start   : 拉起目标（已运行的后端会自动重启；中间件已运行则复用）
#    restart : 先停后起目标
#    target  : all(默认) | backend | middleware
#    -y      : 对所有确认类提示回答 yes（此处主要用于未来扩展）
#  依赖中间件：见 SERVICES 配置（可选，自动检测并启动，并记录自有 PID）
# ============================================================
set -o pipefail

BACKEND_PORT=8095
BACKEND_SIG="TransactionLabApplication"        # 主启动类签名，用于精准识别本项目进程（避免误杀其它项目）
RUN_MODE="spring-boot"              # spring-boot | console
MAIN_CLASS="com.example.txlab.TransactionLabApplication"          # 仅 console 模式需要
PROJECT_DIR="$(cd "$(dirname "$0")"; pwd)"
RUN_DIR="$PROJECT_DIR/.run"; PID_BACKEND="$RUN_DIR/backend.pid"
mkdir -p "$RUN_DIR"

# 工具链自适应（本机路径兜底；留空则交给 PATH）
JDK="/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
[ -d "$JDK" ] && export JAVA_HOME="$JDK"
MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"
[ -x "$MVN_BIN" ] && MVN="$MVN_BIN" || MVN="mvn"

# 中间件联动启动（留空 SERVICES_ENTRIES 跳过）：每项 "服务名|端口|连接串"
SERVICES_ENTRIES=()
MW_PID_DIR="$RUN_DIR"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info(){ echo -e "${CYAN}[INFO]${NC} $*"; }
ok(){ echo -e "${GREEN}[ OK ]${NC} $*"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $*"; }
err(){ echo -e "${RED}[FAIL]${NC} $*"; }

# ---- 参数解析 ----
ACTION="start"; TARGET="all"; YES=0
for a in "$@"; do
  case "$a" in
    start|restart) ACTION="$a" ;;
    all|backend|frontend|middleware) TARGET="$a" ;;
    -y|--yes) YES=1 ;;
    *) warn "未知参数: $a（忽略）" ;;
  esac
done

# ---- 中间件联动（复制自 middleware-lib.sh）----
MW_MAX_WAIT=15
mw_check_port(){ lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
mw_get_pid(){     lsof -iTCP:"$1" -sTCP:LISTEN -n -P -t 2>/dev/null; }
mw_start_one(){
  local name=$1 port=$2 conn=$3
  echo ">>> 检查 $name (端口 $port) ..."
  if mw_check_port "$port"; then echo -e "${GREEN}[SKIP] $name 已在运行 (PID: $(mw_get_pid "$port" || echo ?), 地址: $conn)${NC}"; return 0; fi
  echo -e "${YELLOW}[INFO] $name 未运行，正在启动...${NC}"
  case $name in
    redis)    brew services start redis     2>/dev/null || redis-server --daemonize yes || { echo -e "${RED}[FAIL] $name 启动失败${NC}"; return 1; } ;;
    mysql)    brew services start mysql     2>/dev/null || mysql.server start           || { echo -e "${RED}[FAIL] $name 启动失败${NC}"; return 1; } ;;
    rabbitmq) brew services start rabbitmq  2>/dev/null || { echo -e "${RED}[FAIL] $name 启动失败${NC}"; return 1; } ;;
    postgres) brew services start postgresql 2>/dev/null || { echo -e "${RED}[FAIL] $name 启动失败${NC}"; return 1; } ;;
    *) echo -e "${RED}[FAIL] 未知服务 $name，跳过${NC}"; return 1 ;;
  esac
  local r=0
  while ! mw_check_port "$port" && [ $r -lt "$MW_MAX_WAIT" ]; do echo -n "."; sleep 1; r=$((r+1)); done
  echo ""
  if mw_check_port "$port"; then
    local pid; pid=$(mw_get_pid "$port"); echo -e "${GREEN}[ OK ] $name 启动成功 (PID: ${pid:-?}, 地址: $conn)${NC}"
    [ -n "$pid" ] && echo "$pid" > "$MW_PID_DIR/mw_${name}.pid"; return 0
  else echo -e "${RED}[FAIL] $name 启动超时${NC}"; return 1; fi
}
mw_start_all(){ local entry s p c; if [ ${#SERVICES_ENTRIES[@]} -gt 0 ]; then for entry in "${SERVICES_ENTRIES[@]}"; do IFS='|' read -r s p c <<< "$entry"; mw_start_one "$s" "$p" "$c" || { echo -e "${RED}[FAIL] 关键中间件 $s 启动失败，终止启动流程${NC}"; exit 1; }; done; fi; }
mw_stop_all(){
  local leaked=0 entry s p c pidf pid
  if [ ${#SERVICES_ENTRIES[@]} -gt 0 ]; then
    for entry in "${SERVICES_ENTRIES[@]}"; do
      IFS='|' read -r s p c <<< "$entry"
      pidf="$MW_PID_DIR/mw_${s}.pid"
      if [ -f "$pidf" ]; then
        pid=$(cat "$pidf")
        echo ">>> 停止 $s (本脚本启动, PID: $pid, 端口 $p) ..."
        kill "$pid" 2>/dev/null; sleep 2
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
mw_print_status(){ local entry s p c; if [ ${#SERVICES_ENTRIES[@]} -gt 0 ]; then for entry in "${SERVICES_ENTRIES[@]}"; do IFS='|' read -r s p c <<< "$entry"; if mw_check_port "$p"; then echo -e "  - ${GREEN}$s: 运行中 ($c)${NC}"; else echo -e "  - ${RED}$s: 未运行${NC}"; fi; done; fi; }

# ---- 后端启停 ----
# 兜底清理：按主类签名找进程，但只处理真正以 java 启动的进程。
# 不用裸 pkill -f，避免误伤命令行里恰好包含该字符串的 shell / 编辑器 / grep 本身。
kill_backend_by_signature(){
  local p cmd
  for p in $(pgrep -f "$BACKEND_SIG" 2>/dev/null); do
    cmd=$(ps -o command= -p "$p" 2>/dev/null) || continue
    case "$cmd" in
      *java*"$BACKEND_SIG"*) info "按主类签名清理残留 (PID $p) ..."; kill "$p" 2>/dev/null; return 0 ;;
    esac
  done
  return 1
}
backend_running(){ mw_check_port "$BACKEND_PORT" || { [ -f "$PID_BACKEND" ] && kill -0 "$(cat "$PID_BACKEND")" 2>/dev/null; }; }
stop_backend(){
  local stopped=0
  if [ -f "$PID_BACKEND" ]; then
    local pid; pid=$(cat "$PID_BACKEND")
    if kill -0 "$pid" 2>/dev/null; then info "停止后端进程 (PID $pid) ..."; kill "$pid" 2>/dev/null; stopped=1; fi
    rm -f "$PID_BACKEND"
  fi
  local p; p=$(mw_get_pid "$BACKEND_PORT")
  if [ -n "$p" ]; then info "释放后端端口 $BACKEND_PORT (PID $p) ..."; kill "$p" 2>/dev/null; stopped=1; fi
  kill_backend_by_signature && stopped=1
  if [ $stopped -eq 0 ]; then ok "后端未运行（无需停止）"; return 0; fi
  sleep 2
  p=$(mw_get_pid "$BACKEND_PORT")
  [ -n "$p" ] && { kill -9 $p 2>/dev/null; sleep 1; }
  if mw_check_port "$BACKEND_PORT"; then warn "后端端口 $BACKEND_PORT 仍被占用，请手动释放"; else ok "后端已停止（端口 $BACKEND_PORT 已释放）"; fi
}
start_backend(){
  info "启动后端 (端口 $BACKEND_PORT, 模式 $RUN_MODE) ..."
  cd "$PROJECT_DIR"
  if [ "$RUN_MODE" = "spring-boot" ]; then
    nohup "$MVN" -q spring-boot:run -Dspring-boot.run.arguments=--server.port=$BACKEND_PORT > "$PROJECT_DIR/backend.log" 2>&1 &
    echo $! > "$PID_BACKEND"
    ok "后端进程已拉起 (PID $(cat "$PID_BACKEND"))"
    info "等待后端就绪（最长 90s）..."
    local i=0
    while [ $i -lt 90 ]; do
      if curl -s -o /dev/null -m 2 "http://localhost:$BACKEND_PORT/" 2>/dev/null; then ok "后端就绪: http://localhost:$BACKEND_PORT"; return 0; fi
      echo -n "."; sleep 2; i=$((i+2))
    done
    err "后端启动超时。最近日志:"; tail -n 20 "$PROJECT_DIR/backend.log"
    kill "$(cat "$PID_BACKEND")" 2>/dev/null; rm -f "$PID_BACKEND"; return 1
  else
    info "编译并运行 (mvn compile exec:java -Dexec.mainClass=$MAIN_CLASS) ..."
    echo "----------------------------------------------------"
    "$MVN" compile exec:java -Dexec.mainClass=$MAIN_CLASS
    echo "----------------------------------------------------"
    echo "运行结束。按任意键退出..."
    read -n 1 -s -r
  fi
}
do_backend(){
  if [ "$ACTION" = "restart" ]; then stop_backend; fi
  if [ "$ACTION" = "start" ] && backend_running; then warn "检测到后端已在运行，自动重启..."; stop_backend; fi
  start_backend
}
do_middleware(){ if [ "$ACTION" = "restart" ]; then mw_stop_all; fi; mw_start_all; }

echo "===================================================="
echo "  spring-transaction-boundary-lab  ${ACTION} (目标: $TARGET)"
echo "===================================================="
case "$TARGET" in
  backend)    do_backend || exit 1 ;;
  middleware) do_middleware ;;
  all|*)      do_middleware; do_backend || exit 1 ;;
esac

# 状态汇总
echo ""
echo -e "${GREEN}================ 服务状态 ================${NC}"
if [ "$TARGET" = "backend" ] || [ "$TARGET" = "all" ]; then
  if mw_check_port "$BACKEND_PORT"; then echo -e "  - ${GREEN}后端: 运行中 (http://localhost:$BACKEND_PORT)${NC}"; else echo -e "  - ${RED}后端: 未运行${NC}"; fi
fi
if [ "${#SERVICES_ENTRIES[@]}" -gt 0 ] && { [ "$TARGET" = "middleware" ] || [ "$TARGET" = "all" ]; }; then
  echo -e "  ${CYAN}·${NC} 中间件:"
  mw_print_status
fi
echo -e "${GREEN}==========================================${NC}"
if [ "$RUN_MODE" = "spring-boot" ]; then
  echo "  日志: tail -f $PROJECT_DIR/backend.log"
  echo "  关闭: ./stop.command   (重启: ./restart.command)"
  trap 'trap - EXIT INT TERM; stop_backend; echo; echo "已停止后端服务（窗口关闭即停服）"' EXIT INT TERM
  tail -f "$PROJECT_DIR/backend.log"
fi
