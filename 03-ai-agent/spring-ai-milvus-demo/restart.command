#!/bin/bash
# spring-ai-milvus-demo · 一键重启 (macOS 双击)
# 用法：restart.command [all|backend] [-y]   双击 = all
set -u
DIR="$(cd "$(dirname "$0")"; pwd)"
TARGET="${1:-all}"
case "$TARGET" in
  all|backend) ;;
  *) echo "[FAIL] 未知目标：$TARGET（仅支持 all / backend）"; exit 1 ;;
esac
echo "===================================================="
echo "  AI Token Cost Platform · 重启 ($TARGET)"
echo "===================================================="
"$DIR/stop.command" -y
echo "[INFO] 开始重新启动 ..."
exec "$DIR/start.command" "$TARGET"
