#!/bin/bash
BACKEND_PORT=8080
BACKEND_SIG="DemoApplication"
echo "===================================================="
echo "  spring-ai-milvus-demo 一键停止"
echo "===================================================="
echo "关闭后端进程并释放端口 $BACKEND_PORT ..."
pkill -f "$BACKEND_SIG" 2>/dev/null && echo "  ✓ 已终止后端进程" || echo "  · 未发现运行中的后端进程"
pkill -f "spring-boot:run" 2>/dev/null || true
PID=$(lsof -ti tcp:$BACKEND_PORT 2>/dev/null)
if [ -n "$PID" ]; then kill -9 $PID 2>/dev/null && echo "  ✓ 已释放端口 $BACKEND_PORT"; fi
echo "完成。"
read -n 1 -s -r -p "按任意键退出..."
