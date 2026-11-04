@echo off
chcp 65001 >nul
rem =====================================================================================
rem  MQ 消息队列实战 · 一键启动 / 重启（Windows 双击运行）
rem
rem  双击   → start all（中间件 + 后端 + 前端；已运行则自动「先停后起」）
rem  命令行 → start.bat [start^|restart] [all^|backend^|frontend^|middleware] [-y]
rem  端口   → set BACKEND_PORT=8081 后再执行 start.bat
rem =====================================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

set PROJECT_NAME=MQ 消息队列实战
if "%BACKEND_PORT%"=="" set BACKEND_PORT=8080
if "%FRONTEND_PORT%"=="" set FRONTEND_PORT=5173
set BACKEND_SIG=mq-practice-backend.jar
set HEALTH_PATH=/api/mq/queues
set JAR_PATH=backend\mq-practice-backend\target\mq-practice-backend.jar
set RUN_DIR=.run
set LOG_DIR=logs
if not exist "%RUN_DIR%" mkdir "%RUN_DIR%"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

set ACTION=start
set TARGET=all

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="start" set ACTION=start
if /i "%~1"=="restart" set ACTION=restart
if /i "%~1"=="all" set TARGET=all
if /i "%~1"=="backend" set TARGET=backend
if /i "%~1"=="frontend" set TARGET=frontend
if /i "%~1"=="middleware" set TARGET=middleware
if /i "%~1"=="-y" shift
shift
goto parse_args
:args_done

echo.
echo ==================================================
echo   %PROJECT_NAME% · %ACTION% (目标: %TARGET%)
echo ==================================================
echo.

rem ------------------------------------------------ 依赖检查
where java >nul 2>&1
if errorlevel 1 (
  echo [FAIL] 未找到 java，请安装 JDK 17 并加入 PATH
  goto end
)
where mvn >nul 2>&1
if errorlevel 1 (
  echo [FAIL] 未找到 mvn，请安装 Maven 并加入 PATH
  goto end
)
echo [ OK ] java / mvn 就绪

if "%TARGET%"=="all" call :mw_start_rabbitmq
if "%TARGET%"=="all" if errorlevel 1 goto end
if "%TARGET%"=="all" call :mw_start_redis
if "%TARGET%"=="all" if errorlevel 1 goto end

if "%TARGET%"=="backend" goto run_backend
if "%TARGET%"=="frontend" goto run_frontend
if "%TARGET%"=="middleware" goto show_status

call :stop_frontend
call :stop_backend
call :start_backend
if errorlevel 1 goto end
call :start_frontend
if errorlevel 1 goto end
goto show_status

:run_backend
if /i "%ACTION%"=="restart" call :stop_backend
call :start_backend
goto show_status

:run_frontend
if /i "%ACTION%"=="restart" call :stop_frontend
call :start_frontend
goto show_status

rem ------------------------------------------------ 中间件
:mw_start_rabbitmq
echo ^>^>^> RabbitMQ (5672)
netstat -ano | findstr ":5672 " | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo [SKIP] RabbitMQ 已在运行（外部进程，本脚本不会停止它）
  exit /b 0
)
docker info >nul 2>&1
if errorlevel 1 (
  echo [FAIL] RabbitMQ 未运行且 Docker Desktop 未启动，请先打开 Docker
  exit /b 1
)
echo [INFO] 拉起 RabbitMQ 容器...
docker compose up -d rabbitmq >> "%LOG_DIR%\middleware.log" 2>&1
call :wait_port 5672 40
if errorlevel 1 (
  echo [FAIL] RabbitMQ 启动超时，查看 logs\middleware.log
  exit /b 1
)
echo docker> "%RUN_DIR%\mw_rabbitmq.mode"
echo [ OK ] RabbitMQ 就绪：amqp://localhost:5672  控制台 http://localhost:15672 (guest/guest)
exit /b 0

:mw_start_redis
echo ^>^>^> Redis (6379)
netstat -ano | findstr ":6379 " | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo [SKIP] Redis 已在运行（外部进程，本脚本不会停止它）
  exit /b 0
)
docker info >nul 2>&1
if errorlevel 1 (
  echo [FAIL] Redis 未运行且 Docker Desktop 未启动
  exit /b 1
)
echo [INFO] 拉起 Redis 容器...
docker compose up -d redis >> "%LOG_DIR%\middleware.log" 2>&1
call :wait_port 6379 40
if errorlevel 1 (
  echo [FAIL] Redis 启动超时，查看 logs\middleware.log
  exit /b 1
)
echo docker> "%RUN_DIR%\mw_redis.mode"
echo [ OK ] Redis 就绪：redis://localhost:6379
exit /b 0

rem ------------------------------------------------ 后端
:start_backend
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo [WARN] 端口 %BACKEND_PORT% 已被占用，尝试关闭本项目进程后重启
  call :stop_backend
  netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul
  if not errorlevel 1 (
    echo [FAIL] 端口 %BACKEND_PORT% 仍被其它程序占用，请换端口：set BACKEND_PORT=8081 ^&^& start.bat
    exit /b 1
  )
)

echo [INFO] 编译打包后端（Maven 多模块）...
call mvn -B -q -DskipTests package -f backend\pom.xml > "%LOG_DIR%\backend-build.log" 2>&1
if errorlevel 1 (
  echo [FAIL] 后端编译失败，最后 25 行日志：
  powershell -NoProfile -Command "Get-Content '%LOG_DIR%\backend-build.log' -Tail 25"
  exit /b 1
)
if not exist "%JAR_PATH%" (
  echo [FAIL] 未找到产物 %JAR_PATH%
  exit /b 1
)
echo [ OK ] 编译成功

echo [INFO] 启动后端 -^> http://localhost:%BACKEND_PORT%
start "MQ-Backend" cmd /c "java -jar %JAR_PATH% --server.port=%BACKEND_PORT% > %LOG_DIR%\backend.log 2>&1"
call :wait_http %BACKEND_PORT% %HEALTH_PATH% 180
if errorlevel 1 (
  echo [FAIL] 后端启动超时，最后 25 行日志：
  powershell -NoProfile -Command "Get-Content '%LOG_DIR%\backend.log' -Tail 25"
  exit /b 1
)
call :find_pid %BACKEND_PORT%
if defined FOUND_PID echo !FOUND_PID!> "%RUN_DIR%\backend.pid"
echo [ OK ] 后端就绪：http://localhost:%BACKEND_PORT%
exit /b 0

rem ------------------------------------------------ 前端
:start_frontend
netstat -ano | findstr ":%FRONTEND_PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo [WARN] 端口 %FRONTEND_PORT% 已被占用，尝试关闭本项目进程后重启
  call :stop_frontend
  netstat -ano | findstr ":%FRONTEND_PORT% " | findstr "LISTENING" >nul
  if not errorlevel 1 (
    echo [FAIL] 端口 %FRONTEND_PORT% 仍被占用，请换端口：set FRONTEND_PORT=5174 ^&^& start.bat
    exit /b 1
  )
)
where node >nul 2>&1
if errorlevel 1 (
  echo [FAIL] 未检测到 node，请安装 Node 18+
  exit /b 1
)
if not exist "frontend\node_modules" (
  echo [INFO] 首次运行，安装前端依赖（npm install，可能需要几分钟）...
  pushd frontend
  call npm install --no-fund --no-audit > "..\%LOG_DIR%\frontend-install.log" 2>&1
  if errorlevel 1 (
    popd
    echo [FAIL] npm install 失败，日志：logs\frontend-install.log
    exit /b 1
  )
  popd
)

echo [INFO] 启动前端（Vite） -^> http://localhost:%FRONTEND_PORT%
set VITE_API_TARGET=http://localhost:%BACKEND_PORT%
start "MQ-Frontend" cmd /c "cd /d %CD%\frontend && set VITE_API_TARGET=http://localhost:%BACKEND_PORT% && npm run dev -- --port %FRONTEND_PORT% --host > ..\%LOG_DIR%\frontend.log 2>&1"
call :wait_http %FRONTEND_PORT% / 90
if errorlevel 1 (
  echo [FAIL] 前端启动失败，最后 20 行日志：
  powershell -NoProfile -Command "Get-Content '%LOG_DIR%\frontend.log' -Tail 20"
  exit /b 1
)
call :find_pid %FRONTEND_PORT%
if defined FOUND_PID echo !FOUND_PID!> "%RUN_DIR%\frontend.pid"
echo [ OK ] 前端就绪：http://localhost:%FRONTEND_PORT%
exit /b 0

rem ------------------------------------------------ 停止
:stop_backend
if exist "%RUN_DIR%\backend.pid" (
  set /p OLD_PID=<"%RUN_DIR%\backend.pid"
  echo [INFO] 停止后端进程 (PID !OLD_PID!)...
  taskkill /PID !OLD_PID! /T /F >nul 2>&1
  del /q "%RUN_DIR%\backend.pid" >nul 2>&1
)
rem 按「java.exe + 主类签名」精准结束，不会误杀其它 Java 程序
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*%BACKEND_SIG%*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
echo [ OK ] 后端已停止
exit /b 0

:stop_frontend
if exist "%RUN_DIR%\frontend.pid" (
  set /p OLD_PID=<"%RUN_DIR%\frontend.pid"
  echo [INFO] 停止前端进程 (PID !OLD_PID!)...
  taskkill /PID !OLD_PID! /T /F >nul 2>&1
  del /q "%RUN_DIR%\frontend.pid" >nul 2>&1
)
rem 按「node.exe + 本项目 vite 路径」精准结束，不会 taskkill /IM node.exe
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='node.exe'\" | Where-Object { $_.CommandLine -like '*mq-practice*' -and $_.CommandLine -like '*vite*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
echo [ OK ] 前端已停止
exit /b 0

rem ------------------------------------------------ 工具
:wait_port
setlocal
set WAIT_PORT=%1
set WAIT_MAX=%2
set /a WAIT_I=0
:wait_port_loop
netstat -ano | findstr ":%WAIT_PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 ( endlocal & exit /b 0 )
timeout /t 1 /nobreak >nul
set /a WAIT_I+=1
if !WAIT_I! lss %WAIT_MAX% goto wait_port_loop
endlocal & exit /b 1

:wait_http
setlocal
set W_PORT=%1
set W_PATH=%2
set W_MAX=%3
set /a W_I=0
:wait_http_loop
for /f %%c in ('curl -s -o NUL -w "%%{http_code}" "http://127.0.0.1:%W_PORT%%W_PATH%" 2^>nul') do set W_CODE=%%c
if "%W_CODE%"=="200" ( endlocal & exit /b 0 )
timeout /t 2 /nobreak >nul
set /a W_I+=2
if !W_I! lss %W_MAX% goto wait_http_loop
endlocal & exit /b 1

:find_pid
set FOUND_PID=
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%1 " ^| findstr "LISTENING"') do set FOUND_PID=%%p
exit /b 0

rem ------------------------------------------------ 状态
:show_status
echo.
echo ---------------- 服务状态 ----------------
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 (echo   [运行中] 后端 API     http://localhost:%BACKEND_PORT%) else (echo   [未运行] 后端 API)
netstat -ano | findstr ":%FRONTEND_PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 (echo   [运行中] 前端控制台   http://localhost:%FRONTEND_PORT%) else (echo   [未运行] 前端控制台)
netstat -ano | findstr ":5672 " | findstr "LISTENING" >nul
if not errorlevel 1 (echo   [运行中] RabbitMQ     http://localhost:15672  guest/guest) else (echo   [未运行] RabbitMQ)
netstat -ano | findstr ":6379 " | findstr "LISTENING" >nul
if not errorlevel 1 (echo   [运行中] Redis        redis://localhost:6379) else (echo   [未运行] Redis)
echo ------------------------------------------
echo   日志：logs\backend.log  logs\frontend.log
echo   重启：restart.bat     关闭：stop.bat
echo ------------------------------------------
echo.

:end
echo.
pause
