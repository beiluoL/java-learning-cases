#!/bin/zsh
# 电商学习项目 · 一键启动（macOS 双击运行）
# 脚本位于 ecommerce-learn-all/ 合并根目录
# 直接对接本机已运行的 MySQL(3306) / Redis(6379)，不依赖 docker
# 流程：依赖探活 → 清理旧实例 → 启动后端(8080) → 启动前端(5173)

cd "$(dirname "$0")"
ROOT="$PWD"                                  # ecommerce-learn-all 合并根目录
BASE="$ROOT/ecommerce-learn"                 # 后端目录
FRONTEND="$ROOT/ecommerce-learn-vue"         # 前端目录

# 补全常用可执行路径（.command 登录 shell 可能未加载）
export PATH="/usr/local/mysql/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH"
# Maven（本机实际安装目录，按需修改）
export PATH="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin:$PATH"
# Node / npm：按常见位置探测后加入 PATH（覆盖 homebrew / nvm / 托管运行时）
for _d in /Users/beiluo/.workbuddy/binaries/node/versions/22.22.2/bin /opt/homebrew/bin /usr/local/bin /usr/local/nvm/versions/node/*/bin; do
  if [ -x "$_d/npm" ]; then export PATH="$_d:$PATH"; break; fi
done

# 防御：清除可能污染 server.port 的环境变量（如某些沙箱注入的 SERVER__PORT）
unset SERVER__PORT SERVER__HOST SERVER_PORT 2>/dev/null

echo "========================================="
echo "   电商学习项目 · 一键启动（本地模式）"
echo "========================================="

# 0. 依赖探活
echo "[0/4] 依赖探活 ..."
MYSQL_OK=0
if mysql -uroot -proot -h127.0.0.1 -P3306 -e "SELECT 1;" >/dev/null 2>&1; then
  echo "      ✓ MySQL(127.0.0.1:3306) 可连接"
  MYSQL_OK=1
else
  echo "      ✗ MySQL 连接失败，请确认本机 MySQL 已启动且账号为 root/root"
fi
if redis-cli -h 127.0.0.1 -p 6379 ping 2>/dev/null | grep -q PONG; then
  echo "      ✓ Redis(127.0.0.1:6379) 可连接"
else
  echo "      ✗ Redis 连接失败，请确认本机已执行 redis-server"
fi
if [ "$MYSQL_OK" -ne 1 ]; then
  echo "依赖未就绪，退出。"
  exit 1
fi

# 1. 清理已在运行的实例（幂等：已启动则先停后起）
echo "[1/4] 检查并清理已运行实例(8080/5173) ..."
lsof -ti tcp:8080 2>/dev/null | xargs -r kill -9 2>/dev/null
lsof -ti tcp:5173 2>/dev/null | xargs -r kill -9 2>/dev/null
sleep 1

# 2. 启动后端
echo "[2/4] 启动后端 (mvn spring-boot:run) ..."
cd "$BASE" || exit 1
# 显式指定端口，确保不被环境变量覆盖
nohup mvn -q spring-boot:run -Dspring-boot.run.arguments="--server.port=8080" > "$BASE/backend.log" 2>&1 & disown
echo "      日志 -> $BASE/backend.log"

# 3. 启动前端
echo "[3/4] 启动前端 (npm run dev) ..."
cd "$FRONTEND" || exit 1
nohup npm run dev > "$FRONTEND/frontend.log" 2>&1 & disown
echo "      日志 -> $FRONTEND/frontend.log"

# 4. 等待并探活
echo "[4/4] 等待后端就绪 ..."
for i in $(seq 1 40); do
  if curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/auth/register -X POST -H "Content-Type: application/json" -d '{}' 2>/dev/null | grep -qE "200|400|500"; then
    echo "      后端已响应 ✓"
    break
  fi
  sleep 3
done

echo ""
echo "========================================="
echo "   启动完成"
echo "   前端：    http://localhost:5173"
echo "   后端接口：http://localhost:8080/api"
echo "   接口文档：http://localhost:8080/doc.html"
echo "========================================="
echo "   关闭请双击 stop.command"
