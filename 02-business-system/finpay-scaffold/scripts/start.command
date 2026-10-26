#!/bin/bash
# ============================================================
# FinPay 一键启动（macOS 双击运行）
# 拉起：业务服务(8080) + 网关(8088) + Vue 前端(5173)
# 端口/进程自校验；若已运行则先停后起（自动重启）
# ============================================================
DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DIR" || exit 1
RUN_DIR="$DIR/scripts/.run"
mkdir -p "$RUN_DIR"
LOG="$RUN_DIR/start.log"
: > "$LOG"

JAVA_PORTS=(8080 8088)
FE_PORT=5173
MVN="$(command -v mvn || echo /Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn)"
NODE="$(command -v node || echo node)"
NPM="$(command -v npm || echo npm)"

echo "[INFO] 项目根目录: $DIR"
echo "[INFO] 日志: $LOG"

# 端口占用检测
port_pid() { lsof -ti tcp:"$1" 2>/dev/null; }

stop_existing() {
  echo "[INFO] 检测并清理已运行实例..."
  for p in "${JAVA_PORTS[@]}" "$FE_PORT"; do
    pid=$(port_pid "$p")
    if [ -n "$pid" ]; then
      echo "[WARN] 端口 $p 被 PID $pid 占用，正在停止..."
      kill "$pid" 2>/dev/null; sleep 2
      kill -9 "$pid" 2>/dev/null
    fi
  done
  # 兜底清理 PID 文件
  for f in "$RUN_DIR"/*.pid; do
    [ -f "$f" ] || continue
    pid=$(cat "$f"); kill "$pid" 2>/dev/null; rm -f "$f"
  done
}

stop_existing

# 前置依赖检查
check_dep() {
  if ! command -v "$1" >/dev/null 2>&1 && [ ! -x "$1" ]; then
    echo "[FAIL] 未找到 $1，请先安装并加入 PATH" | tee -a "$LOG"
    exit 1
  fi
}
check_dep "$MVN"; check_dep "$NODE"

# 启动业务服务
echo "[INFO] 启动业务服务 (finpay-business :8080) ..."
nohup "$MVN" -q -pl finpay-business -am spring-boot:run > "$RUN_DIR/business.log" 2>&1 &
echo $! > "$RUN_DIR/business.pid"

# 启动网关
echo "[INFO] 启动网关 (finpay-gateway :8088) ..."
nohup "$MVN" -q -pl finpay-gateway -am spring-boot:run > "$RUN_DIR/gateway.log" 2>&1 &
echo $! > "$RUN_DIR/gateway.pid"

# 启动前端
echo "[INFO] 启动前端 (finpay-web :5173) ..."
cd "$DIR/finpay-web" || exit 1
if [ ! -d node_modules ]; then
  echo "[INFO] 首次运行，安装前端依赖 (npm install) ..."
  "$NPM" install >> "$LOG" 2>&1 || { echo "[FAIL] 前端依赖安装失败，详见 $LOG"; exit 1; }
fi
nohup "$NPM" run dev > "$RUN_DIR/web.log" 2>&1 &
echo $! > "$RUN_DIR/web.pid"
cd "$DIR" || exit 1

echo ""
echo "[ OK ] 已全部启动（后台运行，可关闭本窗口，进程继续）"
echo "---------------------------------------------------"
echo " 管理后台:  http://localhost:5173  (账号 admin / admin123)"
echo " 接口文档:  http://localhost:8080/doc.html"
echo " 业务 API:  http://localhost:8080"
echo " 网关 API:  http://localhost:8088"
echo "---------------------------------------------------"
echo " 日志目录:  $RUN_DIR"
echo " 停止脚本:  双击 scripts/stop.command"
