@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
REM ============================================================
REM  Spring Vue Learn 一键关闭（Windows）
REM  双击：默认 stop all。命令行： stop.bat [all|backend|frontend] [-y] [--force]
REM  精准关停本项目后端(:8080)与前端(:5173)，无误杀、幂等。
REM ============================================================
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
cd /d "%SCRIPT_DIR%" || exit /b 1

set "BACKEND_PORT=8080"
set "FRONTEND_PORT=5173"
set "RUN_DIR=%SCRIPT_DIR%\.run"
set "PID_BACKEND=%RUN_DIR%\backend.pid"
set "PID_FRONTEND=%RUN_DIR%\frontend.pid"

set "TARGET=all"
set "YES=0"
set "FORCE=0"
:parse_args
if "%1"=="" goto :done_parse
if /i "%1"=="all"        set "TARGET=all"
if /i "%1"=="backend"    set "TARGET=backend"
if /i "%1"=="frontend"   set "TARGET=frontend"
if /i "%1"=="-y"         set "YES=1"
if /i "%1"=="--force"    set "FORCE=1"
shift
goto :parse_args
:done_parse

echo ===== 正在关闭 %PROJECT_NAME% 服务 (目标: %TARGET%) =====

if /i "%TARGET%"=="backend"  call :stop_backend
if /i "%TARGET%"=="all"      call :stop_backend
if /i "%TARGET%"=="frontend" call :stop_frontend
if /i "%TARGET%"=="all"      call :stop_frontend

echo ===== 关闭完成 =====
echo 按任意键退出...
pause >nul
exit /b 0

:stop_backend
set "stopped=0"
if exist "%PID_BACKEND%" for /f %%p in (%PID_BACKEND%) do taskkill /PID %%p /F >nul 2>nul && set "stopped=1" && echo [INFO] 停止后端进程 (PID %%p^)
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%BACKEND_PORT% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul && set "stopped=1"
if "%stopped%"=="0" (echo [ OK ] 后端未运行（无需停止） & goto :eof)
timeout /t 2 /nobreak >nul
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%BACKEND_PORT% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul 2>nul && echo [WARN] 后端端口 %BACKEND_PORT% 仍被占用，请手动释放 || echo [ OK ] 后端已停止（端口 %BACKEND_PORT% 已释放^)
goto :eof

:stop_frontend
set "stopped=0"
if exist "%PID_FRONTEND%" for /f %%p in (%PID_FRONTEND%) do taskkill /PID %%p /F >nul 2>nul && set "stopped=1" && echo [INFO] 停止前端进程 (PID %%p^)
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%FRONTEND_PORT% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul && set "stopped=1"
if "%stopped%"=="0" (echo [ OK ] 前端未运行（无需停止） & goto :eof)
timeout /t 2 /nobreak >nul
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%FRONTEND_PORT% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul
netstat -ano | findstr ":%FRONTEND_PORT% " | findstr "LISTENING" >nul 2>nul && echo [WARN] 前端端口 %FRONTEND_PORT% 仍被占用，请手动释放 || echo [ OK ] 前端已停止（端口 %FRONTEND_PORT% 已释放^)
goto :eof
