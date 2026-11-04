#!/bin/bash
# =====================================================================================
#  MQ 消息队列实战 · 一键重启（macOS 双击运行）
#
#  双击   → 重启全部（先 stop all，再 start all）
#  命令行 → ./restart.command [all|backend|frontend|middleware] [-y]
#           ./restart.command backend   仅重启后端（不碰中间件，最快）
# =====================================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

TARGET="all"
for a in "$@"; do
  case "$a" in
    all|backend|frontend|middleware) TARGET="$a" ;;
    -y|--yes|--no-wait) : ;;
  esac
done

echo ">>> 停止（目标: $TARGET）..."
bash "$SCRIPT_DIR/stop.command" "$TARGET" --no-wait
STOP_CODE=$?
if [ $STOP_CODE -ne 0 ]; then
  echo "[FAIL] 停止阶段失败，为避免冲突不再执行启动。请先手动排查端口占用。"
  exit 1
fi

echo ""
echo ">>> 启动（目标: $TARGET）..."
bash "$SCRIPT_DIR/start.command" "start" "$TARGET" --no-wait
