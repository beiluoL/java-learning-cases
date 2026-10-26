@echo off
chcp 65001 >nul
setlocal
set PROJECT_DIR=%~dp0
cd /d %PROJECT_DIR%

echo ====================================================
echo   docker · 停止 Milvus + Attu
echo ====================================================
docker compose -f milvus-standalone-docker-compose.yml down 2>nul
docker compose -f attu-compose.yml down 2>nul
echo 完成。
pause
