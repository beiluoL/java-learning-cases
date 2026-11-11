@echo off
chcp 65001 >nul
setlocal
set "PROJECT_DIR=%~dp0"
set "RUN_DIR=%PROJECT_DIR%.run"
set "NO_PAUSE=%~1"

echo ====================================================
echo   AI Token Cost Platform - 停止服务
echo ====================================================
echo 仅结束 .run\*.pid 记录的本项目进程，不按端口/进程名查杀。

call :stop_one backend 8091
call :stop_one frontend 5174

echo [INFO] 完成。Redis / MySQL / Milvus 等共享服务请自行管理。
if /I not "%NO_PAUSE%"=="-y" pause
exit /B 0

:stop_one
set "KEY=%~1"
set "PORT=%~2"
set "PF=%RUN_DIR%\%KEY%.pid"
if not exist "%PF%" (
  echo [SKIP] %KEY% 无正在运行的服务
  exit /B 0
)
set "PID="
set /P PID=<"%PF%"
if "%PID%"=="" (
  echo [WARN] %KEY% PID 文件为空，清理
  del "%PF%" >nul 2>&1
  exit /B 0
)
tasklist /FI "PID eq %PID%" 2>nul | findstr /R "[0-9]" | findstr "%PID%" >nul
if errorlevel 1 (
  echo [WARN] %KEY% PID %PID% 已不存在，清理过期文件
) else (
  taskkill /PID %PID% /T /F >nul 2>&1
  echo [ OK ] %KEY% 已停止 ^(PID %PID%, 端口 %PORT%^)
)
del "%PF%" >nul 2>&1
exit /B 0
