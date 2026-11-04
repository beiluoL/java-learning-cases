@echo off
chcp 65001 >nul
rem =====================================================================================
rem  MQ 消息队列实战 · 一键停止（Windows 双击运行）
rem
rem  双击   → stop.bat（前端 → 后端 → 本脚本拉起的中间件）
rem  命令行 → stop.bat [all^|backend^|frontend^|middleware]
rem =====================================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

if "%BACKEND_PORT%"=="" set BACKEND_PORT=8080
if "%FRONTEND_PORT%"=="" set FRONTEND_PORT=5173
set BACKEND_SIG=mq-practice-backend.jar
set RUN_DIR=.run

set TARGET=all
set SILENT=0
:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="all" set TARGET=all
if /i "%~1"=="backend" set TARGET=backend
if /i "%~1"=="frontend" set TARGET=frontend
if /i "%~1"=="middleware" set TARGET=middleware
if /i "%~1"=="--silent" set SILENT=1
shift
goto parse_args
:args_done

echo.
echo ==================================================
echo   MQ 消息队列实战 · 一键停止 (目标: %TARGET%)
echo ==================================================
echo.

if "%TARGET%"=="frontend" goto stop_frontend_only
if "%TARGET%"=="backend" goto stop_backend_only
if "%TARGET%"=="middleware" goto stop_mw_only

call :stop_frontend
call :stop_backend
call :stop_middleware
goto summary

:stop_frontend_only
call :stop_frontend
goto summary

:stop_backend_only
call :stop_backend
goto summary

:stop_mw_only
call :stop_middleware
goto summary

rem ------------------------------------------------ 停止前端
:stop_frontend
if exist "%RUN_DIR%\frontend.pid" (
  set /p FRONT_PID=<"%RUN_DIR%\frontend.pid"
  echo [INFO] 停止前端进程 (PID !FRONT_PID!)...
  taskkill /PID !FRONT_PID! /T /F >nul 2>&1
  del /q "%RUN_DIR%\frontend.pid" >nul 2>&1
)
rem 精准匹配：node.exe 且命令行包含本项目路径与 vite（绝不 taskkill /IM node.exe）
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='node.exe'\" | Where-Object { $_.CommandLine -like '*mq-practice*' -and $_.CommandLine -like '*vite*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
netstat -ano | findstr ":%FRONTEND_PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 (echo [WARN] 前端端口 %FRONTEND_PORT% 仍被占用) else (echo [ OK ] 前端已停止)
exit /b 0

rem ------------------------------------------------ 停止后端
:stop_backend
if exist "%RUN_DIR%\backend.pid" (
  set /p BACK_PID=<"%RUN_DIR%\backend.pid"
  echo [INFO] 停止后端进程 (PID !BACK_PID!)...
  taskkill /PID !BACK_PID! /T /F >nul 2>&1
  del /q "%RUN_DIR%\backend.pid" >nul 2>&1
)
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*%BACKEND_SIG%*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 (echo [WARN] 后端端口 %BACKEND_PORT% 仍被占用) else (echo [ OK ] 后端已停止)
exit /b 0

rem ------------------------------------------------ 停止中间件（仅本脚本拉起的）
:stop_middleware
if exist "%RUN_DIR%\mw_rabbitmq.mode" (
  echo [INFO] 停止 RabbitMQ 容器...
  docker compose stop rabbitmq >nul 2>&1
  del /q "%RUN_DIR%\mw_rabbitmq.mode" >nul 2>&1
  echo [ OK ] RabbitMQ 已停止
) else (
  netstat -ano | findstr ":5672 " | findstr "LISTENING" >nul
  if not errorlevel 1 (echo [INFO] RabbitMQ 此前已在运行（非本脚本启动），保留不动) else (echo [INFO] RabbitMQ 未运行)
)
if exist "%RUN_DIR%\mw_redis.mode" (
  echo [INFO] 停止 Redis 容器...
  docker compose stop redis >nul 2>&1
  del /q "%RUN_DIR%\mw_redis.mode" >nul 2>&1
  echo [ OK ] Redis 已停止
) else (
  netstat -ano | findstr ":6379 " | findstr "LISTENING" >nul
  if not errorlevel 1 (echo [INFO] Redis 此前已在运行（非本脚本启动），保留不动) else (echo [INFO] Redis 未运行)
)
exit /b 0

rem ------------------------------------------------ 汇总
:summary
del /q "%RUN_DIR%\backend.pid" >nul 2>&1
del /q "%RUN_DIR%\frontend.pid" >nul 2>&1
echo.
echo ---------------- 端口状态 ----------------
for %%p in (%BACKEND_PORT% %FRONTEND_PORT% 5672 6379) do (
  netstat -ano | findstr ":%%p " | findstr "LISTENING" >nul
  if not errorlevel 1 (echo   端口 %%p 仍在监听) else (echo   端口 %%p 已释放)
)
echo ------------------------------------------
echo.
if "%SILENT%"=="1" exit /b 0
pause
