#!/usr/bin/env bash
#
# 一键停止（macOS 双击 / 命令行均可）
#
# 用途     ：停止由 start.command 启动的本项目前后端进程
# 用法     ：stop.command [-y]       -y 表示不等待按键（供 restart.command 调用）
# 参数     ：-y  静默模式，不打印「按任意键退出」
# 环境变量 ：BACKEND_PORT（默认 8091）、FRONTEND_PORT（默认 5174，仅用于展示）
# 执行前提 ：无
# 副作用   ：仅向 .run/*.pid 记录的 PID 发送 SIGTERM，并删除过期 PID 文件
# 失败处理 ：单个进程无法退出时打印警告并保留 PID 文件，由使用者人工处理；
#            整体始终以 0 退出，保证 restart / 幂等调用不被中断
#
# 安全约定：绝不按端口或进程名宽泛查杀，绝不使用 kill -9。
#
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
RUN_DIR="$PROJECT_DIR/.run"
BACKEND_PORT="${BACKEND_PORT:-8091}"
FRONTEND_PORT="${FRONTEND_PORT:-5174}"
NO_PAUSE="${1:-}"

echo "===================================================="
echo "  AI Cost Governance Platform · 停止服务"
echo "===================================================="

# 逐个服务精确停止：优先 SIGTERM，等 10 秒，不升级为 kill -9
stop_one() {
  local key="$1" label="$2" port="$3" pid_file pid i
  pid_file="$RUN_DIR/$key.pid"

  if [ ! -f "$pid_file" ]; then
    echo "[SKIP] $label 无正在运行的服务"
    return 0
  fi

  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if ! [[ "${pid:-}" =~ ^[0-9]+$ ]]; then
    echo "[WARN] $label 的 PID 文件内容非法（'${pid:-}'），已清理"
    rm -f "$pid_file"
    return 0
  fi

  if ! kill -0 "$pid" 2>/dev/null; then
    echo "[WARN] $label PID $pid 已不存在，清理过期文件"
    rm -f "$pid_file"
    return 0
  fi

  kill "$pid" 2>/dev/null || true
  for i in $(seq 1 10); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done

  if kill -0 "$pid" 2>/dev/null; then
    echo "[FAIL] $label PID $pid 未能退出，请手动处理（未使用 kill -9，PID 文件已保留）"
    return 0
  fi

  echo "[ OK ] $label 已停止（PID ${pid}，端口 ${port}）"
  rm -f "$pid_file"
  return 0
}

stop_one backend "后端" "$BACKEND_PORT"
stop_one frontend "前端" "$FRONTEND_PORT"

echo "[INFO] 完成。外部共享服务（Redis / MySQL / Milvus）不由本脚本管理。"

if [ "$NO_PAUSE" != "-y" ] && [ -t 0 ]; then
  read -n 1 -s -r -p "按任意键退出..." || true
  echo
fi

exit 0
