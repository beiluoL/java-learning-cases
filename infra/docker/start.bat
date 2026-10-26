@echo off
chcp 65001 >nul
setlocal
set PROJECT_DIR=%~dp0
cd /d %PROJECT_DIR%

echo ====================================================
echo   docker · 启动 Milvus + Attu
echo ====================================================
where docker >nul 2>&1
if errorlevel 1 (
  echo 未检测到 docker，请先安装 Docker Desktop 并启动。
  pause
  exit /b 1
)

echo [1/2] 先停止并移除旧容器（释放资源）...
docker compose -f milvus-standalone-docker-compose.yml down 2>nul
docker compose -f attu-compose.yml down 2>nul

echo [2/2] 启动 Milvus 与 Attu ...
docker compose -f milvus-standalone-docker-compose.yml up -d
docker compose -f attu-compose.yml up -d

echo 启动完成（首次需拉取镜像并初始化，约 1~2 分钟变 healthy）
echo   Milvus 地址 : 127.0.0.1:19530  (HTTP 9091)
echo   Attu 控制台 : http://localhost:9530
echo   停止        : 运行 stop.bat
pause
