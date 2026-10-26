@echo off
chcp 65001 >nul
setlocal
set BACKEND_PORT=8080
set FRONTEND_PORT=8010
set BACKEND_SIG=RagDocQaApplication
set PROJECT_DIR=%~dp0
set BACKEND_DIR=%PROJECT_DIR%rag-doc-qa-backend
set FRONTEND_DIR=%PROJECT_DIR%rag-doc-qa-frontend

echo ====================================================
echo   rag-doc-qa-system 一键启动
echo ====================================================
echo [1/4] 关闭已运行进程并释放端口 (%BACKEND_PORT%/%FRONTEND_PORT%) ...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :%BACKEND_PORT% ^| findstr LISTENING') do taskkill /PID %%a /F >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :%FRONTEND_PORT% ^| findstr LISTENING') do taskkill /PID %%a /F >nul 2>&1
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%RagDocQaApplication%%'" get ProcessId 2^>nul ^| findstr [0-9]') do taskkill /PID %%p /F >nul 2>&1
taskkill /F /IM node.exe >nul 2>&1
timeout /t 2 >nul

echo [2/4] 启动后端 ...
start "后端-日志" cmd /k "cd /d %BACKEND_DIR% && mvn spring-boot:run"

echo [3/4] 启动前端 ...
if not exist "%FRONTEND_DIR%\node_modules" (start "前端-安装" cmd /k "cd /d %FRONTEND_DIR% && npm install")
start "前端-日志" cmd /k "cd /d %FRONTEND_DIR% && npm run dev"

echo [4/4] 启动完成
echo   前端访问地址: http://localhost:%FRONTEND_PORT%
echo   后端访问地址: http://localhost:%BACKEND_PORT%
echo   (依赖 Milvus 与 LLM Key；请先启动 docker 目录的 Milvus)
echo   日志窗口    : 后端-日志 / 前端-日志 / 前端-安装
echo   停止服务    : 运行 stop.bat
pause
