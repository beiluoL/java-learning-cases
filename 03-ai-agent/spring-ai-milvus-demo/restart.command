#!/usr/bin/env bash
#
# 一键重启（macOS 双击 / 命令行均可）
#
# 用途     ：先精确停止本项目进程，再按指定模式重新启动
# 用法     ：restart.command [all|backend]      双击等价于 all
# 参数     ：all / backend（透传给 start.command）
# 环境变量 ：BACKEND_PORT / FRONTEND_PORT / JAVA_HOME（见 start.command）
# 执行前提 ：同 start.command
# 副作用   ：等价于 stop.command -y 后立即 start.command
# 失败处理 ：停止阶段始终成功（幂等）；启动失败由 start.command 打印原因并非零退出
#
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:-all}"

case "$TARGET" in
  all|backend) ;;
  *) echo "[FAIL] 未知目标：${TARGET}（仅支持 all / backend）"; exit 2 ;;
esac

echo "===================================================="
echo "  AI Cost Governance Platform · 重启 ($TARGET)"
echo "===================================================="

"$SCRIPT_DIR/stop.command" -y
echo "[INFO] 开始重新启动 ..."
# exec 让 start.command 接管当前进程，避免多一层 shell 影响 PID 记录
exec "$SCRIPT_DIR/start.command" "$TARGET"
