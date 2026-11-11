#!/usr/bin/env bash
#
# 一键启动（macOS 双击 / 命令行均可）
#
# 用途     ：构建后端 jar 并启动后端（可选启动前端 dev server）
# 用法     ：start.command [all|backend]     双击等价于 all
# 参数     ：all      启动后端 + 前端（前端依赖缺失时明确告警并跳过）
#            backend  只启动后端
# 环境变量 ：BACKEND_PORT（默认 8091）、FRONTEND_PORT（默认 5174）
#            JAVA_HOME   若已设置且主版本在 17~22 之间则优先使用
# 执行前提 ：项目根目录可写；MySQL/Redis 由 infra/docker 与外部服务提供，本脚本不拉起
# 副作用   ：1) 会先精确停止本项目上一次启动的进程（见 stop.command）
#            2) 会在 .run/ 下写入 *.pid 与 backend.log / frontend.log
#            3) 会执行 mvn package，覆盖 target/ 下产物
# 失败处理 ：任一环节失败都会打印原因并以非零码退出，窗口保持以便查看
#
# 安全约定：不使用 pkill / kill -9 / 按端口杀进程；只管理 .run/*.pid 记录的自身进程。
#
set -Eeuo pipefail

# 出错时打印行号与失败命令，便于双击场景（看不到调用栈）快速定位
trap 'rc=$?; echo "[FAIL] 第 $LINENO 行执行失败（退出码 ${rc}）：$BASH_COMMAND" >&2' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
RUN_DIR="$PROJECT_DIR/.run"
BACKEND_PORT="${BACKEND_PORT:-8091}"
FRONTEND_PORT="${FRONTEND_PORT:-5174}"
MODE="${1:-all}"

case "$MODE" in
  all|backend) ;;
  *) echo "[FAIL] 未知模式：${MODE}（仅支持 all / backend）"; exit 2 ;;
esac

mkdir -p "$RUN_DIR"

echo "===================================================="
echo "  AI Cost Governance Platform · 启动 ($MODE)"
echo "===================================================="

# ---------- 工具链探测：不硬编码任何用户绝对路径 ----------

# 读取某个 JDK 主版本号，失败时输出空串
java_major() {
  "$1/bin/java" -version 2>&1 | awk -F'"' '/version/ {split($2, a, "."); print (a[1]=="1" ? a[2] : a[1]); exit}'
}

# 可用性判定：17~22 合法。JDK 23 起 javac 默认关闭隐式注解处理，
# Lombok 会静默失效并报出数百处「找不到符号 getXxx()」，故必须排除。
is_usable_jdk() {
  local home="$1" major
  [ -x "$home/bin/java" ] || return 1
  major="$(java_major "$home" 2>/dev/null || true)"
  [ -n "$major" ] || return 1
  [ "$major" -ge 17 ] && [ "$major" -le 22 ]
}

# 探测顺序：显式 JAVA_HOME → macOS java_home -v 17 → 常见安装目录
resolve_java_home() {
  local candidate
  if [ -n "${JAVA_HOME:-}" ] && is_usable_jdk "$JAVA_HOME"; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi
  if [ -x /usr/libexec/java_home ]; then
    candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [ -n "$candidate" ] && is_usable_jdk "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi
  for candidate in /Library/Java/JavaVirtualMachines/*/Contents/Home \
                   "$HOME"/.sdkman/candidates/java/17* \
                   /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
                   /usr/lib/jvm/*17*; do
    if is_usable_jdk "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

resolve_mvn() {
  local candidate
  if command -v mvn >/dev/null 2>&1; then
    command -v mvn
    return 0
  fi
  for candidate in /opt/homebrew/bin/mvn /usr/local/bin/mvn \
                   "$HOME"/.sdkman/candidates/maven/current/bin/mvn; do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

if ! RESOLVED_JAVA_HOME="$(resolve_java_home)"; then
  echo "[FAIL] 未找到可用的 JDK（要求主版本 17~22）。"
  echo "       当前 JAVA_HOME=${JAVA_HOME:-<未设置>}，PATH 中的 java 主版本=$(java -version 2>&1 | awk -F'\"' '/version/{split($2,a,\".\");print a[1];exit}' || echo 未知)"
  echo "       修复：安装 JDK 17 并设置 export JAVA_HOME=<JDK17 路径>"
  echo "       注意：JDK 23+ 会导致 Lombok 注解处理失效，编译报「找不到符号 getXxx()」。"
  read -n 1 -s -r -p "按任意键退出..." || true
  exit 1
fi
export JAVA_HOME="$RESOLVED_JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
echo "[INFO] JAVA_HOME=${JAVA_HOME}（主版本 $(java_major "$JAVA_HOME")）"

if ! MVN="$(resolve_mvn)"; then
  echo "[FAIL] 未找到 mvn，请安装 Maven 3.8+ 并加入 PATH。"
  read -n 1 -s -r -p "按任意键退出..." || true
  exit 1
fi
echo "[INFO] Maven=$MVN"

# ---------- 1) 精确停掉本项目残留实例（自动重启语义） ----------
"$PROJECT_DIR/stop.command" -y >/dev/null 2>&1 || true

# ---------- 2) 端口占用：只检查不清理，被别人占用就明确拒绝 ----------
if lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[FAIL] 端口 $BACKEND_PORT 已被其他进程占用，拒绝启动且不会终止该进程。"
  lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN | head -5
  read -n 1 -s -r -p "按任意键退出..." || true
  exit 1
fi

echo "[INFO] 依赖自检：Redis / MySQL / Milvus 由 infra/docker 与外部服务提供，本脚本不擅自拉起"

# ---------- 3) 打包后端 ----------
echo "[1/3] 打包后端 ($MVN -DskipTests package) ..."
cd "$PROJECT_DIR"
if ! "$MVN" -q -DskipTests package; then
  echo "[FAIL] Maven 打包失败，请查看上方错误（依赖缺失 / 编译错误）"
  read -n 1 -s -r -p "按任意键退出..." || true
  exit 1
fi

JAR="$(ls -t "$PROJECT_DIR"/target/*.jar 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ]; then
  echo "[FAIL] 未找到可执行 jar，启动终止"
  read -n 1 -s -r -p "按任意键退出..." || true
  exit 1
fi
echo "[INFO] 产物 $JAR"

# ---------- 4) 启动后端 ----------
echo "[2/3] 启动后端（端口 ${BACKEND_PORT}）..."
nohup java -jar "$JAR" --server.port="$BACKEND_PORT" >"$RUN_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" >"$RUN_DIR/backend.pid"

ready=0
for _ in $(seq 1 60); do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "[FAIL] 后端进程启动后退出，日志末尾："
    tail -30 "$RUN_DIR/backend.log"
    rm -f "$RUN_DIR/backend.pid"
    read -n 1 -s -r -p "按任意键退出..." || true
    exit 1
  fi
  code="$(curl -s -o /dev/null -m 2 -w '%{http_code}' "http://127.0.0.1:$BACKEND_PORT/actuator/health" 2>/dev/null || echo 000)"
  if [ "$code" = "200" ]; then
    ready=1
    break
  fi
  sleep 2
done

if [ "$ready" -ne 1 ]; then
  echo "[FAIL] 后端 120 秒内未就绪，请查看 $RUN_DIR/backend.log"
  echo "[INFO] 已保留 PID 文件以便 stop.command 精确清理（PID ${BACKEND_PID}）"
  read -n 1 -s -r -p "按任意键退出..." || true
  exit 1
fi
echo "[ OK ] 后端已就绪（PID ${BACKEND_PID}，健康检查 200）"

# ---------- 5) 启动前端 ----------
if [ "$MODE" = "all" ]; then
  echo "[3/3] 启动前端（端口 ${FRONTEND_PORT}）..."
  if [ ! -f "$PROJECT_DIR/frontend/package.json" ]; then
    echo "[SKIP] 未找到 frontend/package.json，跳过前端"
  elif [ ! -d "$PROJECT_DIR/frontend/node_modules" ]; then
    echo "[WARN] 前端依赖未安装，已跳过。请先执行：cd frontend && npm install"
  else
    (
      cd "$PROJECT_DIR/frontend"
      nohup npm run dev -- --port "$FRONTEND_PORT" --strictPort >"$RUN_DIR/frontend.log" 2>&1 &
      printf '%s\n' "$!" >"$RUN_DIR/frontend.pid"
    )
    echo "[ OK ] 前端已启动（http://localhost:${FRONTEND_PORT}）"
  fi
else
  echo "[3/3] backend 模式：跳过前端"
fi

echo ""
echo "  后端地址 : http://localhost:$BACKEND_PORT"
echo "  健康检查 : http://localhost:$BACKEND_PORT/actuator/health"
echo "  前端地址 : http://localhost:$FRONTEND_PORT"
echo "  后端日志 : $RUN_DIR/backend.log"
echo "  停止服务 : 运行 stop.command（只停本项目 PID）"
echo ""

# 仅在交互终端下跟踪日志；非交互（如被其他脚本调用）直接返回，避免挂起
if [ -t 1 ]; then
  echo "[INFO] 窗口保持打开展示实时日志，Ctrl+C 只退出日志跟踪，不停止服务。"
  tail -f "$RUN_DIR/backend.log"
else
  echo "[INFO] 非交互环境，跳过日志跟踪。"
fi
