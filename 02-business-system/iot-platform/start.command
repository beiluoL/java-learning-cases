#!/bin/bash
# =============================================================================
# IoT 大数据平台 - 一键启动（macOS 双击运行）
# 依次拉起后端（Spring Boot）与前端（Vite），窗口保持以便查看实时日志。
# 关闭请运行 stop.command，或直接关闭本终端窗口。
# =============================================================================
cd "$(dirname "$0")" || exit 1

mkdir -p logs
echo "=================================================="
echo "  IoT 大数据平台 启动中..."
echo "  后端: http://localhost:8080  前端: http://localhost:5173"
echo "=================================================="

# ---------- 后端 ----------
if [ ! -f backend/iot-web/target/iot-web-1.0.0.jar ]; then
  echo "[1/2] 未检测到后端 jar，开始编译（首次较慢）..."
  ( cd backend && mvn -q -DskipTests package ) || { echo "后端编译失败，请检查 Maven 环境"; exit 1; }
fi
echo "[1/2] 启动后端 Spring Boot..."
( cd backend/iot-web && java -jar target/iot-web-1.0.0.jar > ../../logs/backend.log 2>&1 ) &
BACKEND_PID=$!
echo "      后端进程 PID=$BACKEND_PID"

# ---------- 前端 ----------
echo "[2/2] 准备前端依赖..."
( cd frontend && [ -d node_modules ] || npm install ) || { echo "前端依赖安装失败，请检查 Node 环境"; exit 1; }
echo "[2/2] 启动前端 Vite..."
( cd frontend && npm run dev > ../logs/frontend.log 2>&1 ) &
FRONTEND_PID=$!
echo "      前端进程 PID=$FRONTEND_PID"

echo ""
echo "启动完成！请稍候，浏览器访问 http://localhost:5173"
echo "后端日志: logs/backend.log   前端日志: logs/frontend.log"
echo "按 Ctrl+C 或关闭窗口不会自动停止服务，请运行 stop.command 停止。"
echo ""

# 保持窗口打开，等待子进程
wait
