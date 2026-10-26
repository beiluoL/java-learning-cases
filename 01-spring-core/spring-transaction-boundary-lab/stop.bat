@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
REM ============================================================
REM  spring-transaction-boundary-lab 一键停止（Windows）
REM  双击运行：默认 stop all（停止后端，并自动停止「本脚本拉起」的中间件）。
REM  命令行： stop.bat [all|backend|middleware] [-y] [--force] [-c]
REM    -y      确认提示统一 yes
REM    --force 跳过宽限直接 SIGKILL（用于卡死进程）
REM    -c      停止中间件前交互确认（默认不询问，保持一键式）
REM  安全：仅按主类签名 / 端口 / 本脚本记录的中间件 PID 清理，绝不误杀其它项目。
REM ============================================================
set "PROJECT_NAME=spring-transaction-boundary-lab"
set "BACKEND_PORT=8095"
set "BACKEND_SIG=TransactionLabApplication"
set "PROJECT_DIR=%~dp0"
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
cd /d "%PROJECT_DIR%" || exit /b 1
set "RUN_DIR=%PROJECT_DIR%\.run"
set "PID_BACKEND=%RUN_DIR%\backend.pid"

set "TARGET=all"
set "YES=0"
set "FORCE=0"
set "CONFIRM=0"
:parse_args
if "%1"=="" goto :done_parse
if /i "%1"=="all"       set "TARGET=all"
if /i "%1"=="backend"   set "TARGET=backend"
if /i "%1"=="middleware" set "TARGET=middleware"
if /i "%1"=="-y"        set "YES=1"
if /i "%1"=="--force"   set "FORCE=1"
if /i "%1"=="-c"        set "CONFIRM=1"
shift
goto :parse_args
:done_parse

echo [INFO] 正在关闭 %PROJECT_NAME% 服务 (目标: %TARGET%) ...

if /i "%TARGET%"=="backend" call :stop_backend
if /i "%TARGET%"=="all"     call :stop_backend
if /i "%TARGET%"=="middleware" call :maybe_stop_mw
if /i "%TARGET%"=="all"        call :maybe_stop_mw

echo [ OK ]  关闭完成。
echo 按任意键退出...
pause >nul
exit /b 0

REM ============ 子程序 ============
:maybe_stop_mw
if "%CONFIRM%"=="1" (
  set /p c="确认停止本脚本拉起的中间件? [y/N]: "
  if /i not "!c!"=="y" (echo [WARN] 已跳过中间件停止（按用户取消） & goto :eof)
)
call :stop_mw
goto :eof

:stop_backend
set "stopped=0"
if exist "%PID_BACKEND%" for /f %%p in (%PID_BACKEND%) do taskkill /PID %%p /F >nul 2>nul && set "stopped=1" && echo [INFO] 停止后端进程 (PID %%p)
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%BACKEND_PORT% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul && set "stopped=1"
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%%BACKEND_SIG%%%'" get ProcessId 2^>nul ^| findstr [0-9]') do taskkill /PID %%p /F >nul 2>nul && set "stopped=1"
if "%stopped%"=="0" (echo [ OK ] 后端未运行（无需停止） & goto :eof)
if not "%FORCE%"=="1" timeout /t 2 /nobreak >nul
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%BACKEND_PORT% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul 2>nul && echo [WARN] 后端端口 %BACKEND_PORT% 仍被占用，请手动释放 || echo [ OK ] 后端已停止（端口 %BACKEND_PORT% 已释放）
goto :eof

:stop_mw
set "any=0"
for %%f in ("%RUN_DIR%\mw_*.pid") do (
  if exist "%%f" (
    set "any=1"
    set /p mwid=<"%%f"
    echo [INFO] 停止中间件 (PID !mwid!^) ...
    taskkill /PID !mwid! /F >nul 2>nul
    if not "%FORCE%"=="1" timeout /t 2 /nobreak >nul
    del /q "%%f" >nul 2>nul
  )
)
if "%any%"=="0" echo [INFO] 无本脚本拉起的中间件需停止（外部已有服务未触碰）
goto :eof
