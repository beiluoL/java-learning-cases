@echo off
chcp 65001 >nul
setlocal
set "PROJECT_DIR=%~dp0"
set "RUN_DIR=%PROJECT_DIR%.run"
set "BACKEND_PORT=8091"
set "FRONTEND_PORT=5174"
set "MODE=%~1"
if "%MODE%"=="" set "MODE=all"
if not exist "%RUN_DIR%" mkdir "%RUN_DIR%"

echo ====================================================
echo   AI Token Cost Platform - 启动
echo ====================================================

call "%PROJECT_DIR%stop.bat" -y >nul 2>&1

netstat -aon | findstr ":%BACKEND_PORT%" | findstr LISTENING >nul
if %errorlevel%==0 (
  echo [FAIL] 端口 %BACKEND_PORT% 已被其他进程占用，拒绝启动且不会终止该进程。
  pause
  exit /B 1
)

echo [INFO] 依赖自检：Redis / MySQL / Milvus 由 infra\docker 提供
echo [1/3] 打包后端 ...
cd /d "%PROJECT_DIR%"
call mvn -q -DskipTests package
if errorlevel 1 (
  echo [FAIL] Maven 打包失败，请查看上方错误
  pause
  exit /B 1
)
set "JAR="
for /f "delims=" %%j in ('dir /b /o-d target\*.jar 2^>nul') do if not defined JAR set "JAR=%PROJECT_DIR%target\%%j"
if not defined JAR (
  echo [FAIL] 未找到可执行 jar
  pause
  exit /B 1
)

echo [2/3] 启动后端 ^(端口 %BACKEND_PORT%^) ...
start "AI-Cost-Backend" /MIN cmd /k "java -jar ""%JAR%"""
set "BPID="
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%spring-ai-milvus-demo%%'" get ProcessId 2^>nul ^| findstr /R "[0-9]"') do if not defined BPID set "BPID=%%p"
if defined BPID (
  >"%RUN_DIR%\backend.pid" echo %BPID%
  echo [ OK ] 后端已启动 ^(PID %BPID%^)
) else (
  echo [WARN] 未能自动记录后端 PID，请通过任务管理器或后端日志窗口手动停止
)

if /I "%MODE%"=="all" (
  echo [3/3] 启动前端 ^(端口 %FRONTEND_PORT%^) ...
  if exist "%PROJECT_DIR%frontend\package.json" (
    if exist "%PROJECT_DIR%frontend\node_modules" (
      start "AI-Cost-Frontend" /MIN cmd /k "cd /d ""%PROJECT_DIR%frontend"" && npm run dev -- --port %FRONTEND_PORT% --strictPort"
      echo [ OK ] 前端已启动
    ) else (
      echo [WARN] 前端依赖未安装，已跳过：cd frontend ^&^& npm install
    )
  ) else (
    echo [SKIP] 尚无 frontend 工程
  )
) else (
  echo [3/3] backend 模式：跳过前端
)

echo.
echo   后端地址 : http://localhost:%BACKEND_PORT%
echo   前端地址 : http://localhost:%FRONTEND_PORT%
echo   停止服务 : 运行 stop.bat（只停本项目 PID）
echo.
pause
