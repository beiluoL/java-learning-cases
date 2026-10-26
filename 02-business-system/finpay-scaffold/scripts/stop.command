#!/bin/bash
# ============================================================
# FinPay 一键停止（macOS 双击运行）
# 精准停止本项目拉起的后端/前端进程（依据 PID 文件），不误杀其他进程
# ============================================================
DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_DIR="$DIR/scripts/.run"

echo "[INFO] 停止 FinPay 相关进程..."

stop_by_pidfile() {
  f="$1"
  if [ -f "$f" ]; then
    pid=$(cat "$f")
    if kill -0 "$pid" 2>/dev/null; then
      echo "[INFO] 停止 PID $pid ($(basename "$f" .pid))"
      kill "$pid" 2>/dev/null; sleep 2; kill -9 "$pid" 2>/dev/null
    fi
    rm -f "$f"
  fi
}

stop_by_pidfile "$RUN_DIR/business.pid"
stop_by_pidfile "$RUN_DIR/gateway.pid"
stop_by_pidfile "$RUN_DIR/web.pid"

# 兜底：清理仍占用端口的残留进程（仅本项目的 8080/8088/5173）
for p in 8080 8088 5173; do
  pid=$(lsof -ti tcp:"$p" 2>/dev/null)
  if [ -n "$pid" ]; then
    echo "[WARN] 端口 $p 仍有进程 $pid，强制停止"
    kill "$pid" 2>/dev/null; sleep 1; kill -9 "$pid" 2>/dev/null
  fi
done

echo "[ OK ] FinPay 已停止"
