#!/bin/bash
# Redis 全栈实战 一键启动（macOS）
# 启动顺序：Redis(容器) → 后端(Spring Boot jar) → 前端(Vue3 vite)
cd "$(dirname "$0")" || exit 1
ROOT="$(pwd)"
mkdir -p "$ROOT/.run"

echo "=========================================="
echo "  Redis 全栈实战 一键启动"
echo "=========================================="

# 1) Redis
echo "[INFO] 启动 Redis 容器..."
docker compose up -d redis 2>&1 | tail -3
sleep 2

# 2) 后端：构建并后台运行 jar（java 进程 PID 直接记录，便于精确停止）
echo "[INFO] 构建并启动后端..."
cd "$ROOT/backend" || exit 1
mvn -q -DskipTests clean package > "$ROOT/.run/backend-build.log" 2>&1
if [ ! -f "target/redis-fullstack-lab.jar" ]; then
  echo "[FAIL] 后端构建失败，请查看 $ROOT/.run/backend-build.log"
  exit 1
fi
nohup java -jar target/redis-fullstack-lab.jar --spring.profiles.active=h2 > "$ROOT/.run/backend.log" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$ROOT/.run/backend.pid"
cd "$ROOT" || exit 1

# 3) 前端：安装依赖并启动 vite
echo "[INFO] 安装并启动前端..."
cd "$ROOT/frontend" || exit 1
npm install > "$ROOT/.run/frontend-install.log" 2>&1
nohup npx vite --port 5173 --host > "$ROOT/.run/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "$FRONTEND_PID" > "$ROOT/.run/frontend.pid"
cd "$ROOT" || exit 1

sleep 3
echo ""
echo "[ OK ] 启动完成（服务在后台运行，可关闭本窗口）"
echo "   前端:        http://localhost:5173"
echo "   后端 API:    http://localhost:8080"
echo "   接口文档:    http://localhost:8080/doc.html"
echo "   Redis 容器:  redis-fullstack-lab"
echo "   运行日志:    .run/backend.log  .run/frontend.log"
echo "   停止:        双击 stop.command"
