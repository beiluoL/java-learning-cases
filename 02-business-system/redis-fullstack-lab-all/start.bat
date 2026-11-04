@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "ROOT=%cd%"
if not exist ".run" mkdir ".run"

echo ====== Redis 全栈实战 一键启动 ======

echo [INFO] 启动 Redis 容器...
docker compose up -d redis
timeout /t 2 >nul

echo [INFO] 构建并启动后端...
cd backend
call mvn -q -DskipTests clean package
if not exist "target\redis-fullstack-lab.jar" (
  echo [FAIL] 后端构建失败
  pause
  exit /b 1
)
for /f "tokens=2 delims==; " %%P in ('wmic process call create "java -jar %ROOT%\backend\target\redis-fullstack-lab.jar --spring.profiles.active=h2" ^| find "ProcessId"') do set "BACKEND_PID=%%P"
echo %BACKEND_PID% > "%ROOT%\.run\backend.pid"
cd %ROOT%

echo [INFO] 安装并启动前端...
cd frontend
call npm install
for /f "tokens=2 delims==; " %%P in ('wmic process call create "cmd /c cd /d %ROOT%\frontend && npx vite --port 5173 --host" ^| find "ProcessId"') do set "FRONTEND_PID=%%P"
echo %FRONTEND_PID% > "%ROOT%\.run\frontend.pid"
cd %ROOT%

timeout /t 3 >nul
echo.
echo [ OK ] 启动完成
echo   前端:        http://localhost:5173
echo   后端 API:    http://localhost:8080
echo   接口文档:    http://localhost:8080/doc.html
echo   停止:        双击 stop.bat
pause
