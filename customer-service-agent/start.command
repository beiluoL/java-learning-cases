#!/bin/bash
# =============================================================================
#  智能客服 Agent —— 一键启动脚本（前端 + 后端）
# =============================================================================
#  用法（任选其一）：
#    1) macOS 鼠标双击本文件 start.command（自动在 Terminal 中运行）
#    2) 终端执行：  bash start.command
#    3) 终端执行：  ./start.command
#
#  功能：
#    ① 先检测并【强制关闭】已运行的同类服务（释放 8080 / 5173 / 4173 端口）
#    ② 按进程名兜底清理残留的 java 后端 / vite 前端进程
#    ③ 后端 jar 缺失时自动构建（首次较慢），前端依赖缺失时自动 npm install
#    ④ 启动后端（Java Spring Boot）-> 等待就绪 -> 启动前端（Vue dev）
#    ⑤ Ctrl+C 一键停止前后端
#
#  访问地址：
#    前端（客服/管理后台）: http://localhost:5173/
#    后端健康检查:          http://localhost:8080/api/health
# =============================================================================

set -e

# 切换到脚本所在目录（双击 .command 时 cwd 是用户家目录，必须修正）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

BACKEND_PORT=8080
FRONTEND_PORT=5173
PREVIEW_PORT=4173

# ---- 颜色输出 ----
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log()  { echo -e "${GREEN}[启动器]${NC} $*"; }
warn() { echo -e "${YELLOW}[启动器]${NC} $*"; }
err()  { echo -e "${RED}[启动器]${NC} $*"; }
info() { echo -e "${CYAN}[启动器]${NC} $*"; }

# ---- 1. 工具探测（优先使用本机已知路径，否则回退 PATH）----
if [ -d "/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home" ]; then
  JAVA_HOME="/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
elif [ -n "${JAVA_HOME:-}" ]; then
  :
else
  JAVA_HOME=""
fi
JAVA_BIN="$(command -v java || true)"
[ -n "$JAVA_HOME" ] && JAVA_BIN="$JAVA_HOME/bin/java"
if [ -z "$JAVA_BIN" ] || ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  err "未找到 java，请先安装 JDK 17+ 后重试。"; exit 1
fi

if [ -x "/Users/beiluo/.workbuddy/binaries/node/versions/22.22.2/bin/npm" ]; then
  NPM_BIN="/Users/beiluo/.workbuddy/binaries/node/versions/22.22.2/bin/npm"
else
  NPM_BIN="$(command -v npm || true)"
fi
[ -z "$NPM_BIN" ] && { err "未找到 npm，请先安装 Node 18+。"; exit 1; }

MVN_BIN="$(command -v mvn || true)"
[ -x "/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn" ] \
  && MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"

log "使用 JAVA: $JAVA_BIN"
log "使用 NPM : $NPM_BIN"
[ -n "$MVN_BIN" ] && info "使用 MVN : $MVN_BIN" || warn "未检测到 maven（jar 已存在时无需构建）"

# ---- 2. 关闭已有同类服务，释放端口 ----
log "第 1 步：检测并关闭已运行的同类服务，释放端口 ..."
kill_port() {
  local port=$1
  local pids
  pids="$(lsof -ti tcp:"$port" 2>/dev/null || true)"
  if [ -n "$pids" ]; then
    warn "端口 $port 被占用，正在释放 (PID: $pids)"
    kill -9 $pids 2>/dev/null || true
    sleep 1
  fi
}
kill_port "$BACKEND_PORT"
kill_port "$FRONTEND_PORT"
kill_port "$PREVIEW_PORT"

# 按进程名兜底，防止端口已释放但进程仍残留
pkill -9 -f "customer-service-agent-1.0.0.jar" 2>/dev/null || true
pkill -9 -f "vite" 2>/dev/null || true
sleep 1
log "端口已释放，准备启动。"

# ---- 3. 准备后端 jar（缺失则构建）----
cd "$SCRIPT_DIR/backend"
JAR="$(ls target/*.jar 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ]; then
  if [ -z "$MVN_BIN" ]; then
    err "未找到后端 jar 且未安装 maven，无法构建后端。请先执行 mvn package。"; exit 1
  fi
  warn "未找到后端 jar，开始构建（首次较慢，需要网络下载依赖）..."
  "$MVN_BIN" package -DskipTests -q || { err "后端构建失败，请检查 maven 配置。"; exit 1; }
  JAR="$(ls target/*.jar 2>/dev/null | head -1 || true)"
fi
[ -z "$JAR" ] && { err "仍未找到可执行 jar，启动中止。"; exit 1; }
log "后端 jar: $JAR"

# ---- 4. 准备前端依赖（缺失则安装）----
cd "$SCRIPT_DIR/frontend"
if [ ! -d "node_modules" ]; then
  warn "前端依赖缺失，执行 npm install ..."
  "$NPM_BIN" install || { err "前端依赖安装失败。"; exit 1; }
fi

# ---- 5. 启动后端 ----
cd "$SCRIPT_DIR/backend"
log "第 2 步：启动后端 (端口 $BACKEND_PORT) ..."
nohup "$JAVA_BIN" -Dserver.port="$BACKEND_PORT" -jar "$JAR" > "$SCRIPT_DIR/backend/run.log" 2>&1 &
BACKEND_PID=$!
log "后端进程 PID=$BACKEND_PID"

READY=0
for i in $(seq 1 60); do
  if curl -s -o /dev/null -m 2 "http://localhost:$BACKEND_PORT/api/health" 2>/dev/null; then
    READY=1; log "后端已就绪（约 ${i}s）"; break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    err "后端进程意外退出，请查看 backend/run.log："; tail -30 "$SCRIPT_DIR/backend/run.log"; exit 1
  fi
  sleep 1
done
[ "$READY" -eq 0 ] && { err "后端启动超时，请查看 backend/run.log："; tail -30 "$SCRIPT_DIR/backend/run.log"; exit 1; }

# ---- 6. 启动前端（前台运行，日志可见；Ctrl+C 会一并清理后端）----
cd "$SCRIPT_DIR/frontend"
log "第 3 步：启动前端 (端口 $FRONTEND_PORT) ..."
echo ""
info "============================================================"
info "  前端（客服对话 / 管理后台）: http://localhost:$FRONTEND_PORT/"
info "  后端健康检查:                http://localhost:$BACKEND_PORT/api/health"
info "  按 Ctrl+C 停止全部服务"
info "============================================================"
echo ""

cleanup() {
  warn "正在停止服务 ..."
  kill -9 "$BACKEND_PID" 2>/dev/null || true
  pkill -9 -f "vite" 2>/dev/null || true
  log "已全部停止。"
}
trap cleanup EXIT INT TERM

# 前端在前台运行（dev 模式，--host 便于同一局域网访问）
"$NPM_BIN" run dev -- --port "$FRONTEND_PORT" --host
