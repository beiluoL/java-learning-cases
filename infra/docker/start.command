#!/bin/bash
# ============================================================
#  docker · 一键启动 Milvus + Attu 基础设施 (macOS)
#  双击运行：先停止旧容器，再启动 Milvus 与 Attu
#  这是向量库等项目的公共依赖，不是独立应用
# ============================================================
set -e
PROJECT_DIR="$(cd "$(dirname "$0")"; pwd)"
cd "$PROJECT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "✗ 未检测到 docker，请先安装 Docker Desktop 并启动。"
  read -n 1 -s -r -p "按任意键退出..."
  exit 1
fi

echo "===================================================="
echo "  docker · 启动 Milvus + Attu"
echo "===================================================="
echo "[1/2] 先停止并移除旧容器（释放资源）..."
docker compose -f milvus-standalone-docker-compose.yml down 2>/dev/null || true
docker compose -f attu-compose.yml down 2>/dev/null || true

echo "[2/2] 启动 Milvus 与 Attu (docker compose up -d) ..."
docker compose -f milvus-standalone-docker-compose.yml up -d
docker compose -f attu-compose.yml up -d
echo ""
echo "启动完成（首次需拉取镜像并初始化，约 1~2 分钟变 healthy）"
echo "  Milvus 地址 : 127.0.0.1:19530  (HTTP 9091)"
echo "  Attu 控制台 : http://localhost:9530"
echo "  查看状态    : docker compose -f milvus-standalone-docker-compose.yml ps"
echo "  停止        : 运行本项目 stop.command"
echo ""
read -n 1 -s -r -p "按任意键退出..."
