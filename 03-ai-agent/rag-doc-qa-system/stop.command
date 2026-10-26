#!/bin/bash
BACKEND_PORT=8080
FRONTEND_PORT=8010
BACKEND_SIG="RagDocQaApplication"
echo "===================================================="
echo "  rag-doc-qa-system 一键停止"
echo "===================================================="
echo "关闭前后端进程并释放端口 ($BACKEND_PORT/$FRONTEND_PORT) ..."
pkill -f "$BACKEND_SIG" 2>/dev/null && echo "  ✓ 已终止后端进程" || echo "  · 未发现运行中的后端进程"
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true
for P in $BACKEND_PORT $FRONTEND_PORT; do
  PID=$(lsof -ti tcp:$P 2>/dev/null)
  if [ -n "$PID" ]; then kill -9 $PID 2>/dev/null && echo "  ✓ 已释放端口 $P"; fi
done
echo "完成。"
read -n 1 -s -r -p "按任意键退出..."
