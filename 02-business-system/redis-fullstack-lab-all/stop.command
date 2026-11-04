#!/bin/bash
# Redis 全栈实战 一键停止（macOS）
# 停止策略：先按记录的 PID 精准停止，再按端口兜底，最后停 Redis 容器；不误杀其他进程。
cd "$(dirname "$0")" || exit 1
ROOT="$(pwd)"
stopped=0

echo "=========================================="
echo "  Redis 全栈实战 一键停止"
echo "=========================================="

# 方式1：按记录的 PID 停止
kill_pidfile() {
  local f="$1" name="$2"
  if [ -f "$f" ]; then
    local pid
    pid=$(cat "$f")
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      echo "[INFO] 停止 $name (PID=$pid)"
      kill "$pid" 2>/dev/null
      sleep 1
      kill -9 "$pid" 2>/dev/null
      stopped=1
    fi
    rm -f "$f"
  fi
}
kill_pidfile "$ROOT/.run/backend.pid" "后端"
kill_pidfile "$ROOT/.run/frontend.pid" "前端"

# 方式2：按端口精准停止（兜底，仅杀占用 8080/5173 的进程，不误伤其他）
for port in 8080 5173; do
  pids=$(lsof -ti tcp:"$port" 2>/dev/null)
  if [ -n "$pids" ]; then
    echo "[INFO] 端口 $port 上的进程: $pids"
    kill $pids 2>/dev/null
    sleep 1
    kill -9 $pids 2>/dev/null
    stopped=1
  fi
done

# 停止 Redis 容器
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "redis-fullstack-lab"; then
  echo "[INFO] 停止 Redis 容器 redis-fullstack-lab"
  docker stop redis-fullstack-lab >/dev/null 2>&1
  stopped=1
fi

if [ "$stopped" -eq 0 ]; then
  echo "[SKIP] 无正在运行的服务"
else
  echo "[ OK ] 已停止相关服务"
fi
