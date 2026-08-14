#!/bin/bash
# =============================================================================
#  customer-service-agent —— 一键启动（前端 + 后端）  [macOS 双击运行]
# =============================================================================
#  流程：① 关闭已运行的同类服务、释放端口 ② 启动后端(Spring Boot) ③ 启动前端(Vue)
#  访问：前端 http://localhost:5173/   后端 http://localhost:8080/api
# =============================================================================
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"; cd "$SCRIPT_DIR"

NAME="customer-service-agent"
BSIGN="com.example.csagent.CsAgentApplication"   # 后端进程唯一签名（主类）
BPORT=8080
BDIR="backend"
FDIR="frontend"
FPORT=5173

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; RED='\033[0;31m'; NC='\033[0m'
log(){ echo -e "${GREEN}[$NAME]${NC} $*"; }
warn(){ echo -e "${YELLOW}[$NAME]${NC} $*"; }
err(){ echo -e "${RED}[$NAME]${NC} $*"; }
info(){ echo -e "${CYAN}[$NAME]${NC} $*"; }

# 工具探测
MVN_BIN="$(command -v mvn || true)"
[ -x "/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn" ] && MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"
NPM_BIN="$(command -v npm || true)"
[ -x "/Users/beiluo/.workbuddy/binaries/node/versions/22.22.2/bin/npm" ] && NPM_BIN="/Users/beiluo/.workbuddy/binaries/node/versions/22.22.2/bin/npm"
[ -z "$MVN_BIN" ] && { err "未找到 mvn，请先安装 Maven 3.6+。"; exit 1; }
[ -z "$NPM_BIN" ] && { err "未找到 npm，请先安装 Node 18+。"; exit 1; }

# ① 关闭已运行同类服务，释放端口
log "第 1 步：关闭已运行的同类服务，释放端口 $BPORT / $FPORT ..."
pkill -9 -f "$BSIGN" 2>/dev/null || true
for p in $BPORT $FPORT; do
  pid="$(lsof -ti tcp:$p 2>/dev/null || true)"
  [ -n "$pid" ] && { warn "释放端口 $p (PID $pid)"; kill -9 "$pid" 2>/dev/null || true; }
done
sleep 1

# ② 启动后端
cd "$SCRIPT_DIR/$BDIR"
log "第 2 步：启动后端 (端口 $BPORT) ..."
nohup "$MVN_BIN" spring-boot:run > "$SCRIPT_DIR/backend-run.log" 2>&1 &
BPID=$!
READY=0
for i in $(seq 1 90); do
  if lsof -ti tcp:$BPORT >/dev/null 2>&1; then READY=1; log "后端已就绪 (~${i}s)"; break; fi
  if ! kill -0 "$BPID" 2>/dev/null; then err "后端启动失败，请查看 backend-run.log："; tail -30 "$SCRIPT_DIR/backend-run.log"; exit 1; fi
  sleep 1
done
[ "$READY" -eq 0 ] && { err "后端启动超时，请查看 backend-run.log："; tail -30 "$SCRIPT_DIR/backend-run.log"; exit 1; }

# ③ 启动前端
cd "$SCRIPT_DIR/$FDIR"
[ ! -d node_modules ] && { warn "安装前端依赖 ..."; "$NPM_BIN" install || { err "前端依赖安装失败。"; exit 1; }; }
log "第 3 步：启动前端 (端口 $FPORT) ..."
echo ""
info "=========================================================="
info "  前端（客服对话 / 管理后台）: http://localhost:$FPORT/"
info "  后端接口基址:                http://localhost:$BPORT/api"
info "  后端健康检查:                http://localhost:$BPORT/api/health"
info "  按 Ctrl+C 停止全部服务"
info "=========================================================="
echo ""
cleanup(){ warn "正在停止服务 ..."; kill -9 "$BPID" 2>/dev/null || true; pkill -9 -f "$BSIGN" 2>/dev/null || true; for p in $BPORT $FPORT; do pid="$(lsof -ti tcp:$p 2>/dev/null || true)"; [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null; done; log "已停止。"; }
trap cleanup EXIT INT TERM
"$NPM_BIN" run dev -- --port "$FPORT" --host
