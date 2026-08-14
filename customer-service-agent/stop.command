#!/bin/bash
# =============================================================================
#  customer-service-agent —— 一键关闭（前端 + 后端）  [macOS 双击运行]
# =============================================================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"; cd "$SCRIPT_DIR"
NAME="customer-service-agent"
BSIGN="com.example.csagent.CsAgentApplication"
BPORT=8080
FPORT=5173
echo "[$NAME] 正在停止服务 ..."
pkill -9 -f "$BSIGN" 2>/dev/null || true
for p in $BPORT $FPORT; do
  pid="$(lsof -ti tcp:$p 2>/dev/null || true)"
  [ -n "$pid" ] && { echo "[$NAME] 释放端口 $p (PID $pid)"; kill -9 "$pid" 2>/dev/null || true; }
done
sleep 1
echo "[$NAME] 已停止，端口已释放。"
