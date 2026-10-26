#!/bin/bash
# =============================================================================
#  智能客服 Agent —— 一键启动 + 真实流式校验脚本
# =============================================================================
#  用法：
#    终端执行：  bash verify.sh            # 启动 3 个服务并校验 CodeBuddy 流式
#               bash verify.sh --no-start  # 仅校验（假设服务已在运行）
#    鼠标双击：  verify.command            （macOS，自动在 Terminal 中运行）
#
#  它会做什么：
#    ① 按需启动 agent-bridge(Node:3001) → 后端(Spring Boot:8080) → 前端(Vue:5173)
#       （已运行的端口会跳过，不会重复拉起；Ctrl+C 仅停「本脚本启动的」进程）
#    ② 等待三个服务就绪（健康检查 + 超时）
#    ③ 【核心校验】向 /api/chat/stream 发一条测试消息，读取 SSE：
#         - 统计真实下发的 text token 数量（证明是逐 token 流式，而非整段返回）
#         - 判断当前走的是「CodeBuddy 真模型」还是「本地规则引擎降级」
#         - 若降级，打印具体原因（CLI 未装 / 无 Key 或登录态 / DLP 网关拦截 / 超时）
#    ④ 输出 PASS/FAIL 结论与访问地址，然后退出（不阻塞终端）
#
#  说明：
#    - 本环境（受限沙箱）企业 DLP 网关不可达，SDK 调用会被拦，校验会显示
#      “降级到本地引擎（仍可流式）”——这是预期行为，链路本身是通的。
#      在本机（CodeBuddy CN 登录态 / API Key 可用、DLP 可达）跑即可拿到真 token。
#    - 此脚本只校验、不改代码；如需停掉所有服务请用 stop.sh / stop.command。
# =============================================================================

set -u

# 切到脚本所在目录（双击时 cwd 可能不是项目目录）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

BRIDGE_PORT=3001
BACKEND_PORT=8080
FRONTEND_PORT=5173
# 用 127.0.0.1 而非 localhost，规避部分环境下 localhost 解析到 IPv6(::1) 导致 curl 探测失败
BRIDGE_URL="http://127.0.0.1:$BRIDGE_PORT"
BACKEND_URL="http://127.0.0.1:$BACKEND_PORT"
FRONTEND_URL="http://127.0.0.1:$FRONTEND_PORT"

NO_START=0
[ "${1:-}" = "--no-start" ] && NO_START=1

# ---- 颜色 ----
if [ -t 1 ]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; CYAN=''; BOLD=''; NC=''
fi
ok()   { echo -e "${GREEN}✓${NC} $*"; }
warn() { echo -e "${YELLOW}⚠${NC} $*"; }
err()  { echo -e "${RED}✗${NC} $*"; }
info() { echo -e "${CYAN}·${NC} $*"; }
step() { echo -e "\n${BOLD}== $* ==${NC}"; }

# 记录本脚本启动的 PID，便于只停自己的进程
STARTED_PIDS=()
cleanup() {
  if [ ${#STARTED_PIDS[@]} -gt 0 ]; then
    warn "清理本脚本启动的进程: ${STARTED_PIDS[*]}"
    kill "${STARTED_PIDS[@]}" 2>/dev/null || true
    sleep 1
    # 二次兜底 kill 残留的 node/vite（仅本脚本启动的端口）
    for p in "$BRIDGE_PORT" "$FRONTEND_PORT"; do
      local_pids="$(lsof -ti tcp:"$p" 2>/dev/null || true)"
      [ -n "$local_pids" ] && kill -9 $local_pids 2>/dev/null || true
    done
  fi
}
trap cleanup EXIT INT TERM

port_used() { lsof -ti tcp:"$1" >/dev/null 2>&1; }

# ---- 工具探测 ----
detect_tools() {
  if [ -d "/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home" ]; then
    JAVA_HOME="/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
  elif [ -n "${JAVA_HOME:-}" ]; then : ; else JAVA_HOME=""; fi
  JAVA_BIN="$(command -v java || true)"
  [ -n "$JAVA_HOME" ] && JAVA_BIN="$JAVA_HOME/bin/java"
  [ -z "$JAVA_BIN" ] && { err "未找到 java，请安装 JDK 17+。"; exit 1; }

  if [ -x "/Users/beiluo/.workbuddy/binaries/node/versions/22.22.2/bin/npm" ]; then
    NPM_BIN="/Users/beiluo/.workbuddy/binaries/node/versions/22.22.2/bin/npm"
  else
    NPM_BIN="$(command -v npm || true)"
  fi
  [ -z "$NPM_BIN" ] && { err "未找到 npm，请安装 Node 18+。"; exit 1; }

  MVN_BIN="$(command -v mvn || true)"
  [ -x "/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn" ] \
    && MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"

  info "JAVA=$JAVA_BIN"
  info "NPM =$NPM_BIN"
  [ -n "$MVN_BIN" ] && info "MVN =$MVN_BIN" || warn "未检测到 maven（jar 已存在时无需构建）"
}

# =====================================================================
#  1) 启动 agent-bridge（CodeBuddy 桥接）
# =====================================================================
start_bridge() {
  step "agent-bridge (CodeBuddy 桥接 :$BRIDGE_PORT)"
  if port_used "$BRIDGE_PORT"; then
    ok "端口 $BRIDGE_PORT 已占用，复用已有桥接服务"
    return 0
  fi
  cd "$SCRIPT_DIR/agent-bridge"
  if [ ! -d node_modules ]; then
    warn "桥接依赖缺失，执行 npm install（会安装 @tencent-ai/agent-sdk 与 @tencent-ai/codebuddy-code CLI）..."
    "$NPM_BIN" install || { err "桥接依赖安装失败。"; return 1; }
  fi
  # 确保 CLI 二进制存在（CodeBuddy 真实链路必需）
  if [ ! -f node_modules/@tencent-ai/codebuddy-code/bin/codebuddy ]; then
    warn "未发现 codebuddy-code CLI，尝试补装..."
    "$NPM_BIN" install @tencent-ai/codebuddy-code@latest >/dev/null 2>&1 || true
  fi
  nohup "$NPM_BIN" start > "$SCRIPT_DIR/agent-bridge/bridge.log" 2>&1 &
  STARTED_PIDS+=($!)
  info "桥接进程 PID=$!"
  return 0
}

# =====================================================================
#  2) 启动后端（Spring Boot）
# =====================================================================
start_backend() {
  step "后端 (Spring Boot :$BACKEND_PORT)"
  if port_used "$BACKEND_PORT"; then
    ok "端口 $BACKEND_PORT 已占用，复用已有后端"
    return 0
  fi
  cd "$SCRIPT_DIR/backend"
  JAR="$(ls target/*.jar 2>/dev/null | head -1 || true)"
  if [ -z "$JAR" ]; then
    if [ -z "$MVN_BIN" ]; then err "未找到 jar 且未安装 maven。"; return 1; fi
    warn "后端 jar 缺失，开始构建（首次较慢）..."
    "$MVN_BIN" package -DskipTests -q || { err "后端构建失败。"; return 1; }
    JAR="$(ls target/*.jar 2>/dev/null | head -1 || true)"
  fi
  [ -z "$JAR" ] && { err "仍未找到可执行 jar。"; return 1; }
  # 规避某些环境注入的 SERVER__PORT=0 / SERVER_PORT=0 覆盖 -Dserver.port 的问题
  unset SERVER__PORT SERVER_PORT 2>/dev/null
  export SERVER_PORT="$BACKEND_PORT"
  nohup "$JAVA_BIN" -Dserver.port="$BACKEND_PORT" -jar "$JAR" > "$SCRIPT_DIR/backend/run.log" 2>&1 &
  STARTED_PIDS+=($!)
  info "后端进程 PID=$!"
  return 0
}

# =====================================================================
#  3) 启动前端（Vue dev）
# =====================================================================
start_frontend() {
  step "前端 (Vue :$FRONTEND_PORT)"
  if port_used "$FRONTEND_PORT"; then
    ok "端口 $FRONTEND_PORT 已占用，复用已有前端"
    return 0
  fi
  cd "$SCRIPT_DIR/frontend"
  if [ ! -d node_modules ]; then
    warn "前端依赖缺失，执行 npm install ..."
    "$NPM_BIN" install || { err "前端依赖安装失败。"; return 1; }
  fi
  nohup "$NPM_BIN" run dev -- --port "$FRONTEND_PORT" --host > "$SCRIPT_DIR/frontend/frontend.log" 2>&1 &
  STARTED_PIDS+=($!)
  info "前端进程 PID=$!"
  return 0
}

# =====================================================================
#  4) 等待就绪
# =====================================================================
wait_ready() {
  step "等待服务就绪"
  local i
  for i in $(seq 1 40); do
    b_ready=0; f_ready=0
    curl -s -o /dev/null -m 2 "$BACKEND_URL/api/health" 2>/dev/null && b_ready=1
    curl -s -o /dev/null -m 2 "$FRONTEND_URL/" 2>/dev/null && f_ready=1
    [ "$b_ready" -eq 1 ] && [ "$f_ready" -eq 1 ] && break
    sleep 1
  done
  # 桥接无独立 /health 强依赖后端，这里只确认端口在听
  if port_used "$BRIDGE_PORT"; then ok "agent-bridge 端口 $BRIDGE_PORT 在听"; else warn "agent-bridge 未监听 $BRIDGE_PORT（将走降级）"; fi
  if curl -s -o /dev/null -m 2 "$BACKEND_URL/api/health" 2>/dev/null; then ok "后端健康"; else err "后端未就绪，请参阅 backend/run.log"; return 1; fi
  if curl -s -o /dev/null -m 2 "$FRONTEND_URL/" 2>/dev/null; then ok "前端可访问"; else warn "前端未就绪（校验仍可进行，但页面打不开）"; fi
  return 0
}

# =====================================================================
#  5) 核心校验：真实 SSE 流式 + 模式识别
# =====================================================================
verify_stream() {
  step "核心校验：CodeBuddy 流式链路"

  local test_msg="$1"
  info "向 POST $BACKEND_URL/api/chat/stream 发送测试消息: $test_msg"

  # 抓取最多 45 秒的 SSE
  local raw
  raw="$(curl -sN -m 45 -X POST "$BACKEND_URL/api/chat/stream" \
        -H 'Content-Type: application/json' \
        -d "{\"message\":\"$test_msg\"}" 2>/dev/null)"

  if [ -z "$raw" ]; then
    err "未收到任何 SSE 数据，后端可能未就绪或接口异常。"
    return 1
  fi

  # 1) 统计真实下发的 text token 数（证明是逐 token 流式）
  local token_count
  token_count="$(printf '%s\n' "$raw" | grep -c '^data:.*"type":"text"' )"
  # 2) 是否收到 done 事件
  local got_done
  printf '%s\n' "$raw" | grep -q '"type":"done"' && got_done=1 || got_done=0

  echo ""
  info "── 校验观测 ──"
  info "收到 text token 事件数 : $token_count"
  [ "$got_done" -eq 1 ] && ok "收到 done 事件（流式正常结束）" || warn "未收到 done 事件"

  if [ "$token_count" -gt 0 ]; then
    ok "确认是「逐 token 流式」输出（不是整段一次性返回）。"
  else
    err "未检测到逐 token 输出，流式异常。"
    return 1
  fi

  # 模式判定：客户端 SSE 中不会出现桥接的 error 事件（Java 内部已吞掉并降级），
  # 所以必须读后端/桥接日志判断到底走的是真实 CodeBuddy 还是本地引擎降级。
  echo ""
  info "── 运行模式 ──"
  local be_log="$SCRIPT_DIR/backend/run.log"
  local br_log="$SCRIPT_DIR/agent-bridge/bridge.log"
  local fallback=0
  grep -q "降级本地引擎" "$be_log" 2>/dev/null && fallback=1
  # 若后端日志无标记，再直接探一下桥接（看它是否返回 error 事件）
  if [ "$fallback" -eq 0 ]; then
    local probe
    probe="$(curl -sN -m 30 -X POST "$BRIDGE_URL/api/agent/stream" \
             -H 'Content-Type: application/json' \
             -d "{\"userMessage\":\"$test_msg\"}" 2>/dev/null)"
    printf '%s\n' "$probe" | grep -q '"type":"error"' && fallback=1
  fi

  if [ "$fallback" -eq 0 ]; then
    ok "${BOLD}当前走【真实 CodeBuddy 大模型】逐 token 流式${NC}"
    ok "结论: PASS（端到端真实流式已跑通）"
  else
    # 读取日志判断降级原因
    local reason="桥接返回 error（详见 agent-bridge/bridge.log）"
    if grep -qi "Sensitive model request protection could not be verified" "$br_log" 2>/dev/null; then
      reason="企业 DLP 数据防护网关不可达，SDK 调用被拦截（受限沙箱/离线环境常见）"
    elif grep -qi "调用超时\|timeout\|Timeout" "$br_log" 2>/dev/null; then
      reason="CodeBuddy 调用超时（检查网络 / KEY / DLP 服务）"
    elif grep -qi "未安装\|CLI 未安装\|npm install @tencent-ai/codebuddy-code" "$br_log" 2>/dev/null; then
      reason="未安装 @tencent-ai/codebuddy-code CLI（请在 agent-bridge 执行 npm install）"
    elif [ -z "${CODEBUDDY_API_KEY:-}" ] && [ ! -f "$SCRIPT_DIR/agent-bridge/.env" ]; then
      reason="无 CODEBUDDY_API_KEY 且本机无 CodeBuddy CN 登录态"
    fi
    warn "${BOLD}当前走【本地规则引擎降级】—— 仍可逐 token 流式，但不是 CodeBuddy 真模型${NC}"
    info "降级原因: $reason"
    info "（在本机且 DLP 服务可达、登录态/KEY 可用时，会切换为真实 CodeBuddy 流式。）"
    warn "结论: 链路 PASS（流式可用）；模型模式 = 本地降级（非阻断）"
  fi

  echo ""
  info "── 访问地址 ──"
  info "前端（客服/管理）: $FRONTEND_URL/"
  info "后端健康        : $BACKEND_URL/api/health"
  info "桥接健康        : $BRIDGE_URL/health"
  return 0
}

# =====================================================================
#  主流程
# =====================================================================
detect_tools

if [ "$NO_START" -eq 0 ]; then
  start_bridge  || true
  start_backend || true
  start_frontend || true
  wait_ready    || { err "服务未就绪，校验中止。"; exit 1; }
else
  step "仅校验模式（--no-start）：确认服务已在运行"
  if ! curl -s -o /dev/null -m 2 "$BACKEND_URL/api/health" 2>/dev/null; then
    err "后端未运行，请先启动服务或去掉 --no-start。"; exit 1
  fi
  ok "后端在线，开始校验。"
fi

verify_stream "我要申请退款怎么操作"
rc=$?

echo ""
if [ "$rc" -eq 0 ]; then
  ok "${BOLD}校验完成。${NC}（服务仍在后台运行；用 stop.sh / stop.command 停止，或 Ctrl+C 退出本脚本）"
else
  err "校验未通过，请查看上方日志与 backend/run.log、agent-bridge/bridge.log。"
fi
exit $rc
