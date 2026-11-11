#!/bin/bash
# AI Token Cost Platform · 一键启动 (macOS 双击)
# 用法：start.command [all|backend]   双击 = all
# 安全约定：不使用 pkill / kill -9 / 按端口杀进程；只管理 .run/*.pid 记录的自身进程。
set -u
PROJECT_DIR="$(cd "$(dirname "$0")"; pwd)"
RUN_DIR="$PROJECT_DIR/.run"
BACKEND_PORT=8091
FRONTEND_PORT=5174
MODE="${1:-all}"
mkdir -p "$RUN_DIR"

echo "===================================================="
echo "  AI Token Cost Platform · 启动"
echo "===================================================="

# 1) 先精确停掉本项目残留实例（自动重启语义），绝不触碰其他项目
"$PROJECT_DIR/stop.command" -y >/dev/null 2>&1 || true

# 2) 端口占用只检查、不清理：被别人占用就明确拒绝
if lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[FAIL] 端口 $BACKEND_PORT 已被其他进程占用，拒绝启动且不会终止该进程。"
  lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN | head -5
  read -n 1 -s -r -p "按任意键退出..."
  exit 1
fi

# 3) 工具链自适应
JDK="/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
[ -d "$JDK" ] && export JAVA_HOME="$JDK"
MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"
if [ -x "$MVN_BIN" ]; then MVN="$MVN_BIN"; else MVN="mvn"; fi

echo "[INFO] 依赖自检：Redis / MySQL / Milvus 由 infra/docker 提供，本脚本不擅自拉起共享服务"
echo "[1/3] 打包后端 (mvn -DskipTests package) ..."
cd "$PROJECT_DIR" || exit 1
if ! "$MVN" -q -DskipTests package; then
  echo "[FAIL] Maven 打包失败，请查看上方错误（依赖缺失 / 编译错误）"
  read -n 1 -s -r -p "按任意键退出..."
  exit 1
fi
JAR=$(ls -t "$PROJECT_DIR"/target/*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "[FAIL] 未找到可执行 jar，启动终止"
  read -n 1 -s -r -p "按任意键退出..."
  exit 1
fi

echo "[2/3] 启动后端 (端口 $BACKEND_PORT) ..."
nohup java -jar "$JAR" > "$RUN_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$RUN_DIR/backend.pid"

ready=0
for i in $(seq 1 60); do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "[FAIL] 后端进程启动后退出，日志末尾："
    tail -30 "$RUN_DIR/backend.log"
    read -n 1 -s -r -p "按任意键退出..."
    exit 1
  fi
  code=$(curl -s -o /dev/null -m 2 -w '%{http_code}' "http://127.0.0.1:$BACKEND_PORT/" 2>/dev/null || echo 000)
  if [ "$code" != "000" ]; then ready=1; break; fi
  sleep 2
done
if [ "$ready" -ne 1 ]; then
  echo "[FAIL] 后端 120 秒内未就绪，请查看 $RUN_DIR/backend.log"
  read -n 1 -s -r -p "按任意键退出..."
  exit 1
fi
echo "[ OK ] 后端已就绪 (PID $BACKEND_PID)"

# 4) 前端（依赖未安装时明确提示，不静默跳过）
if [ "$MODE" = "all" ]; then
  echo "[3/3] 启动前端 (端口 $FRONTEND_PORT) ..."
  if [ -f "$PROJECT_DIR/frontend/package.json" ]; then
    if [ ! -d "$PROJECT_DIR/frontend/node_modules" ]; then
      echo "[WARN] 前端依赖未安装，已跳过：cd frontend && npm install"
    else
      cd "$PROJECT_DIR/frontend" || exit 1
      nohup npm run dev -- --port "$FRONTEND_PORT" --strictPort > "$RUN_DIR/frontend.log" 2>&1 &
      echo $! > "$RUN_DIR/frontend.pid"
      cd "$PROJECT_DIR" || exit 1
      echo "[ OK ] 前端已启动 (http://localhost:$FRONTEND_PORT)"
    fi
  else
    echo "[SKIP] 尚无 frontend 工程"
  fi
else
  echo "[3/3] backend 模式：跳过前端"
fi

echo ""
echo "  后端地址 : http://localhost:$BACKEND_PORT"
echo "  前端地址 : http://localhost:$FRONTEND_PORT"
echo "  后端日志 : $RUN_DIR/backend.log"
echo "  停止服务 : 运行 stop.command（只停本项目 PID）"
echo ""
echo "[INFO] 窗口保持打开展示实时日志，Ctrl+C 只退出日志跟踪，不停止服务。"
tail -f "$RUN_DIR/backend.log"
