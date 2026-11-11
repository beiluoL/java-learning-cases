#!/bin/bash
# AI Token Cost Platform · 精确停止 (macOS 双击)
# 只结束本脚本 PID 文件记录的进程，绝不按端口或进程名宽泛查杀。
set -u
PROJECT_DIR="$(cd "$(dirname "$0")"; pwd)"
RUN_DIR="$PROJECT_DIR/.run"
NO_PAUSE="${1:-}"

echo "===================================================="
echo "  AI Token Cost Platform · 停止服务"
echo "===================================================="

for spec in "backend|后端|8091" "frontend|前端|5174"; do
  IFS='|' read -r key label port <<< "$spec"
  pid_file="$RUN_DIR/$key.pid"
  if [ -f "$pid_file" ]; then
    pid=$(cat "$pid_file")
    if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      for i in $(seq 1 10); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done
      if kill -0 "$pid" 2>/dev/null; then
        echo "[FAIL] $label PID $pid 未能退出，请手动处理（未使用 kill -9）"
      else
        echo "[ OK ] $label 已停止 (PID $pid, 端口 $port)"
      fi
    else
      echo "[WARN] $label 的 PID 文件已过期，清理之（服务未运行）"
    fi
    rm -f "$pid_file"
  else
    echo "[SKIP] $label 无正在运行的服务"
  fi
done

echo "[INFO] 完成。外部共享服务（Redis / MySQL / Milvus）不由本脚本管理。"
if [ "$NO_PAUSE" != "-y" ]; then
  read -n 1 -s -r -p "按任意键退出..."
  echo
fi
