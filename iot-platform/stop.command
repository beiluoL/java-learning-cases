#!/bin/bash
# =============================================================================
# IoT 大数据平台 - 一键关闭（macOS 双击运行）
# 按端口精准停止后端(8080)与前端(5173)，不误杀其他程序。
# =============================================================================
cd "$(dirname "$0")" || exit 1

echo "正在停止 IoT 大数据平台..."

# 停止后端（监听 8080）
BACKEND_PIDS=$(lsof -ti tcp:8080 2>/dev/null)
if [ -n "$BACKEND_PIDS" ]; then
  kill $BACKEND_PIDS 2>/dev/null
  echo "后端已停止 (PID: $BACKEND_PIDS)"
else
  echo "后端未运行"
fi

# 停止前端（监听 5173）
FRONTEND_PIDS=$(lsof -ti tcp:5173 2>/dev/null)
if [ -n "$FRONTEND_PIDS" ]; then
  kill $FRONTEND_PIDS 2>/dev/null
  echo "前端已停止 (PID: $FRONTEND_PIDS)"
else
  echo "前端未运行"
fi

echo "全部服务已停止。"
sleep 1
