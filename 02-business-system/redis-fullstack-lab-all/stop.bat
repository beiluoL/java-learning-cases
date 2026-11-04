@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "ROOT=%cd%"
set stopped=0

echo ====== Redis 全栈实战 一键停止 ======

rem 方式1：按记录的 PID 停止
if exist "%ROOT%\.run\backend.pid" (
  set /p BACKEND_PID=<"%ROOT%\.run\backend.pid"
  if defined BACKEND_PID (
    echo [INFO] 停止后端 (PID=%BACKEND_PID%)
    taskkill /PID %BACKEND_PID% /F >nul 2>&1
    set stopped=1
  )
  del /f "%ROOT%\.run\backend.pid" >nul 2>&1
)
if exist "%ROOT%\.run\frontend.pid" (
  set /p FRONTEND_PID=<"%ROOT%\.run\frontend.pid"
  if defined FRONTEND_PID (
    echo [INFO] 停止前端 (PID=%FRONTEND_PID%)
    taskkill /PID %FRONTEND_PID% /F >nul 2>&1
    set stopped=1
  )
  del /f "%ROOT%\.run\frontend.pid" >nul 2>&1
)

rem 方式2：按端口精准停止（兜底，仅杀占用 8080/5173 的进程）
for %%p in (8080 5173) do (
  for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%%p " ^| findstr "LISTENING"') do (
    echo [INFO] 停止端口 %%p 上的进程 PID=%%a
    taskkill /PID %%a /F >nul 2>&1
    set stopped=1
  )
)

rem 停止 Redis 容器
docker ps --format "{{.Names}}" 2>nul | findstr "redis-fullstack-lab" >nul
if %errorlevel%==0 (
  echo [INFO] 停止 Redis 容器 redis-fullstack-lab
  docker stop redis-fullstack-lab >nul 2>&1
  set stopped=1
)

if %stopped%==0 (
  echo [SKIP] 无正在运行的服务
) else (
  echo [ OK ] 已停止相关服务
)
pause
