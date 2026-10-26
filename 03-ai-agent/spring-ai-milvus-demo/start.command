#!/bin/bash
# ============================================================
#  spring-ai-milvus-demo · 一键启动 (macOS)
#  双击运行：先关闭同类进程并释放端口，再启动后端服务
#  依赖：需先启动 Milvus（见仓库 docker/ 目录）
# ============================================================
set -e
BACKEND_PORT=8080
BACKEND_SIG="DemoApplication"
PROJECT_DIR="$(cd "$(dirname "$0")"; pwd)"

# ---- 工具链自适应 ----
JDK="/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
[ -d "$JDK" ] && export JAVA_HOME="$JDK"
MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"
[ -x "$MVN_BIN" ] && MVN="$MVN_BIN" || MVN="mvn"

echo "===================================================="
echo "  spring-ai-milvus-demo 一键启动"
echo "===================================================="
echo "[1/3] 关闭已运行进程并释放端口 $BACKEND_PORT ..."
pkill -f "$BACKEND_SIG" 2>/dev/null || true
pkill -f "spring-boot:run" 2>/dev/null || true
PID=$(lsof -ti tcp:$BACKEND_PORT 2>/dev/null) && [ -n "$PID" ] && kill -9 $PID 2>/dev/null
sleep 2

echo "[2/3] 启动后端 (端口 $BACKEND_PORT) ..."
cd "$PROJECT_DIR"
nohup "$MVN" -q spring-boot:run > "$PROJECT_DIR/backend.log" 2>&1 &
echo -n "      等待后端就绪"
for i in $(seq 1 45); do
  if curl -s -o /dev/null -m 2 "http://localhost:$BACKEND_PORT/" 2>/dev/null; then
    echo " 就绪"; break
  fi
  echo -n "."
  sleep 2
done

echo "[3/3] 启动完成"
echo ""
echo "  后端访问地址 : http://localhost:$BACKEND_PORT"
echo "  (依赖 Milvus，请确认 docker/ 下的 Milvus 已启动)"
echo "  运行日志     : $PROJECT_DIR/backend.log"
echo "  停止服务     : 运行本项目 stop.command，或按 Ctrl+C"
echo ""
trap 'pkill -f "$BACKEND_SIG" 2>/dev/null; pkill -f "spring-boot:run" 2>/dev/null; echo; echo "已停止后端服务"' EXIT INT TERM
tail -f "$PROJECT_DIR/backend.log"
