@echo off
chcp 65001 >nul
REM ============================================================
REM ssm-learn 一键关闭（Windows：双击 stop.bat 或命令行均可）
REM 精准关停本项目后端（Jetty :8080）与前端（Layui 预览 :8081）。
REM 依据：① 启动脚本记录的 PID 文件（优先）② 明确端口（兜底）。无误杀、幂等。
REM ============================================================
setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "BACKEND_PORT=8080"
set "FRONTEND_PORT=8081"
set "RUN_DIR=%SCRIPT_DIR%\.run"
set "PID_BACKEND=%RUN_DIR%\backend.pid"
set "PID_FRONTEND=%RUN_DIR%\frontend.pid"

echo [INFO] 正在关闭 ssm-learn 服务...
if exist "%PID_FRONTEND%" for /f %%p in (%PID_FRONTEND%) do taskkill /PID %%p /F >nul 2>nul && echo   前端进程 (PID %%p^) 已终止
if exist "%PID_BACKEND%"  for /f %%p in (%PID_BACKEND%)  do taskkill /PID %%p /F >nul 2>nul && echo   后端进程 (PID %%p^) 已终止
timeout /t 2 /nobreak >nul
call :kill_port %FRONTEND_PORT%
call :kill_port %BACKEND_PORT%
if exist "%PID_BACKEND%"   del /q "%PID_BACKEND%"   >nul 2>nul
if exist "%PID_FRONTEND%" del /q "%PID_FRONTEND%" >nul 2>nul
echo [OK]   关闭完成。
echo 按任意键退出...
pause >nul
exit /b 0

:kill_port
set "p=%1"
netstat -ano | findstr ":%p% " | findstr "LISTENING" >nul 2>nul || (echo   端口 %p%：无运行中的服务（已干净） & goto :eof)
echo   端口 %p%：发现监听进程，正在终止...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%p% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul
timeout /t 1 /nobreak >nul
netstat -ano | findstr ":%p% " | findstr "LISTENING" >nul 2>nul && (echo   端口 %p% 仍被占用，请手动检查释放。 ) || echo   端口 %p% 已释放
goto :eof
