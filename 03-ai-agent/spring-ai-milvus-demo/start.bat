@echo off
chcp 65001 >nul
setlocal
set BACKEND_PORT=8080
set BACKEND_SIG=DemoApplication
set PROJECT_DIR=%~dp0

echo ====================================================
echo   spring-ai-milvus-demo 一键启动
echo ====================================================
echo [1/3] 关闭已运行进程并释放端口 %BACKEND_PORT% ...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :%BACKEND_PORT% ^| findstr LISTENING') do taskkill /PID %%a /F >nul 2>&1
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%DemoApplication%%'" get ProcessId 2^>nul ^| findstr [0-9]') do taskkill /PID %%p /F >nul 2>&1
timeout /t 2 >nul

echo [2/3] 启动后端 ...
start "后端-日志" cmd /k "cd /d %PROJECT_DIR% && mvn spring-boot:run"

echo [3/3] 启动完成
echo   后端访问地址: http://localhost:%BACKEND_PORT%
echo   日志窗口    : 后端-日志
echo   (依赖 Milvus，请确认 docker 目录的 Milvus 已启动)
echo   停止服务    : 运行 stop.bat
pause
