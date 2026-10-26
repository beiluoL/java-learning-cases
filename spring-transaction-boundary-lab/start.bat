@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
REM ============================================================
REM  spring-transaction-boundary-lab 一键启动 / 重启（Windows）
REM  双击运行：默认 start all（已运行的后端自动重启）。
REM  命令行： start.bat [start|restart] [all|backend|middleware] [-y]
REM  中间件联动（可选）：在 :start_mw 子程序的  处填写调用，例如：
REM    call :mw_start_service Redis Redis 6379
REM    call :mw_start_service MySQL MySQL80 3306
REM  启动成功后会记录 RUN_DIR\mw_<名>.pid，停止时自动清理（仅清理本脚本拉起的服务）。
REM ============================================================
set "PROJECT_NAME=spring-transaction-boundary-lab"
set "BACKEND_PORT=8095"
set "BACKEND_SIG=TransactionLabApplication"  REM 主启动类签名（精准识别本项目进程）
set "RUN_MODE=spring-boot"          REM spring-boot | console
set "MAIN_CLASS=com.example.txlab.TransactionLabApplication"      REM 仅 console 模式
set "PROJECT_DIR=%~dp0"
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
cd /d "%PROJECT_DIR%" || exit /b 1
if not exist "%PROJECT_DIR%\.run" mkdir "%PROJECT_DIR%\.run"
set "RUN_DIR=%PROJECT_DIR%\.run"
set "PID_BACKEND=%RUN_DIR%\backend.pid"

REM ---- 参数解析 ----
set "ACTION=start"
set "TARGET=all"
:parse_args
if "%1"=="" goto :done_parse
if /i "%1"=="start"     set "ACTION=start"
if /i "%1"=="restart"   set "ACTION=restart"
if /i "%1"=="all"       set "TARGET=all"
if /i "%1"=="backend"   set "TARGET=backend"
if /i "%1"=="middleware" set "TARGET=middleware"
if /i "%1"=="-y"        set "YES=1"
shift
goto :parse_args
:done_parse

echo ====================================================
echo   %PROJECT_NAME% %ACTION% (目标: %TARGET%)
echo ====================================================

if /i "%TARGET%"=="middleware" call :start_mw
if /i "%TARGET%"=="all"        call :start_mw
if /i "%TARGET%"=="backend"    call :do_backend
if /i "%TARGET%"=="all"        call :do_backend

echo.
echo ================ 服务状态 ================
if /i "%TARGET%"=="backend" call :status_backend
if /i "%TARGET%"=="all"     call :status_backend
echo ============================================
if "%RUN_MODE%"=="spring-boot" pause
exit /b 0

REM ============ 子程序 ============
:start_mw
REM 
goto :eof

:mw_start_service
REM %1=显示名 %2=Windows 服务名 %3=端口
echo [INFO] 启动 %1 (端口 %3) ...
netstat -ano | findstr ":%3 " | findstr "LISTENING" >nul 2>nul && (echo [SKIP] %1 已在运行 & goto :eof)
net start %2 >nul 2>nul || sc start %2 >nul 2>nul || (echo [FAIL] %1 启动失败 & pause & exit /b 1)
set /a "mw_r=0"
:wloop
netstat -ano | findstr ":%3 " | findstr "LISTENING" >nul 2>nul && goto wready
if %mw_r% geq 15 (echo [FAIL] %1 启动超时 & pause & exit /b 1)
timeout /t 1 /nobreak >nul
set /a "mw_r+=1"
goto wloop
:wready
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%3 " ^| findstr "LISTENING"') do (echo %%a>"%RUN_DIR%\mw_%1.pid")
echo [ OK ] %1 启动成功（PID 已记录，停止时将自动清理）
goto :eof

:do_backend
if /i "%ACTION%"=="restart" call :stop_backend
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul 2>nul && (echo [WARN] 检测到后端已在运行，自动重启... & call :stop_backend)
if exist "%PID_BACKEND%" (
  for /f %%p in (%PID_BACKEND%) do tasklist /fi "PID eq %%p" | find "%%p" >nul 2>nul && (echo [WARN] 检测到后端已在运行，自动重启... & call :stop_backend)
)
call :start_backend
goto :eof

:stop_backend
set "stopped=0"
if exist "%PID_BACKEND%" for /f %%p in (%PID_BACKEND%) do taskkill /PID %%p /F >nul 2>nul && set "stopped=1" && echo [INFO] 停止后端进程 (PID %%p)
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%BACKEND_PORT% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul && set "stopped=1"
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%%BACKEND_SIG%%%'" get ProcessId 2^>nul ^| findstr [0-9]') do taskkill /PID %%p /F >nul 2>nul && set "stopped=1"
if "%stopped%"=="0" (echo [ OK ] 后端未运行（无需停止） & goto :eof)
timeout /t 2 /nobreak >nul
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%BACKEND_PORT% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul 2>nul && echo [WARN] 后端端口 %BACKEND_PORT% 仍被占用，请手动释放 || echo [ OK ] 后端已停止（端口 %BACKEND_PORT% 已释放）
goto :eof

:start_backend
echo [INFO] 启动后端 (端口 %BACKEND_PORT%, 模式 %RUN_MODE%) ...
if "%RUN_MODE%"=="spring-boot" (
  start "后端-日志" cmd /k "cd /d %PROJECT_DIR% && mvn spring-boot:run"
  echo [ OK ]  后端已拉起（独立日志窗口：后端-日志）
  echo   后端访问地址: http://localhost:%BACKEND_PORT%
  echo   停止服务    : 运行 stop.bat
) else (
  cd /d "%PROJECT_DIR%"
  mvn compile exec:java -Dexec.mainClass=%MAIN_CLASS%
  echo 运行结束。
)
goto :eof

:status_backend
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul 2>nul && echo   [运行中] 后端 (API) : http://localhost:%BACKEND_PORT% || echo   [未运行] 后端 (API)
goto :eof
