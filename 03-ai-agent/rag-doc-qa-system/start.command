#!/bin/bash
# ============================================================
#  rag-doc-qa-system · 一键启动 (macOS)
#  双击运行：先关闭同类进程并释放端口，再启动后端 + 前端
#  依赖：需先启动 Milvus（见仓库 docker/ 目录）并配置 LLM API Key
# ============================================================
set -e
BACKEND_PORT=8080
FRONTEND_PORT=8010
BACKEND_SIG="RagDocQaApplication"
PROJECT_DIR="$(cd "$(dirname "$0")"; pwd)"
BACKEND_DIR="$PROJECT_DIR/rag-doc-qa-backend"
FRONTEND_DIR="$PROJECT_DIR/rag-doc-qa-frontend"

JDK="/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
[ -d "$JDK" ] && export JAVA_HOME="$JDK"
MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"
[ -x "$MVN_BIN" ] && MVN="$MVN_BIN" || MVN="mvn"
NODE_BIN="/Users/beiluo/.workbuddy/binaries/node/versions/22.22.2/bin"
[ -d "$NODE_BIN" ] && export PATH="$NODE_BIN:$PATH"

echo "===================================================="
echo "  rag-doc-qa-system 一键启动"
echo "===================================================="
echo "[1/4] 关闭已运行进程并释放端口 ($BACKEND_PORT/$FRONTEND_PORT) ..."
pkill -f "$BACKEND_SIG" 2>/dev/null || true
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true
for P in $BACKEND_PORT $FRONTEND_PORT; do
  PID=$(lsof -ti tcp:$P 2>/dev/null) && [ -n "$PID" ] && kill -9 $PID 2>/dev/null
done
sleep 2

echo "[2/4] 启动后端 (端口 $BACKEND_PORT) ..."
cd "$BACKEND_DIR"
nohup "$MVN" -q spring-boot:run > "$PROJECT_DIR/backend.log" 2>&1 &
echo -n "      等待后端就绪"
for i in $(seq 1 45); do
  if curl -s -o /dev/null -m 2 "http://localhost:$BACKEND_PORT/" 2>/dev/null; then
    echo " 就绪"; break
  fi
  echo -n "."
  sleep 2
done

echo "[3/4] 启动前端 (端口 $FRONTEND_PORT) ..."
cd "$FRONTEND_DIR"
[ -d node_modules ] || npm install > "$PROJECT_DIR/frontend-install.log" 2>&1
nohup npm run dev > "$PROJECT_DIR/frontend.log" 2>&1 &

echo "[4/4] 启动完成"
echo ""
echo "  前端访问地址 : http://localhost:$FRONTEND_PORT"
echo "  后端访问地址 : http://localhost:$BACKEND_PORT"
echo "  (依赖 Milvus 与 LLM Key；若后端连不上向量库请先启动 docker/ 的 Milvus)"
echo "  后端日志     : $PROJECT_DIR/backend.log"
echo "  前端日志     : $PROJECT_DIR/frontend.log"
echo "  停止服务     : 运行本项目 stop.command，或按 Ctrl+C"
echo ""
trap 'pkill -f "$BACKEND_SIG" 2>/dev/null; pkill -f "spring-boot:run" 2>/dev/null; pkill -f "vite" 2>/dev/null; echo; echo "已停止前后端服务"' EXIT INT TERM
tail -f "$PROJECT_DIR/backend.log" "$PROJECT_DIR/frontend.log"
