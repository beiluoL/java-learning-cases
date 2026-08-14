@echo off
chcp 65001 >nul
REM ============================================================
REM ssm-learn 一键启动（Windows：双击 start.bat 或命令行均可）
REM 同时拉起：后端（SSM + 嵌入式 Jetty :8080）与前端（Layui 预览 :8081）
REM 特性：依赖自检 / 端口-进程自校验 / 已运行则自动重启 / 清晰打印地址 / 失败明确报错
REM 说明：用 PowerShell 以独立进程拉起服务（脱离本窗口，关闭本窗口服务仍在），并记录 PID。
REM ============================================================
setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
cd /d "%SCRIPT_DIR%" || exit /b 1

REM ===== 配置 =====
set "BACKEND_PORT=8080"
set "FRONTEND_PORT=8081"
set "BACKEND_LOG=%SCRIPT_DIR%\logs\backend.log"
set "FRONTEND_LOG=%SCRIPT_DIR%\logs\frontend.log"
set "RUN_DIR=%SCRIPT_DIR%\.run"
set "PID_BACKEND=%RUN_DIR%\backend.pid"
set "PID_FRONTEND=%RUN_DIR%\frontend.pid"
set "BACKEND_URL=http://localhost:%BACKEND_PORT%/"
set "FRONTEND_URL=http://localhost:%FRONTEND_PORT%/"
set "MAX_WAIT=150"

call :preflight
call :autorestart_check
call :mysql_check
call :compile
if errorlevel 1 exit /b 1
call :start_backend
if errorlevel 1 exit /b 1
call :start_frontend
call :status
echo.
echo 服务已在后台运行。按任意键关闭本窗口（服务不中断，停止请用 stop.bat）...
pause >nul
exit /b 0

REM ============ 子程序 ============
:preflight
echo [INFO] 检查运行环境...
where java >nul 2>nul || (echo [FAIL] 未找到 java（JDK 8+），请安装 JDK 并加入 PATH。 & pause & exit /b 1)
where mvn  >nul 2>nul || (echo [FAIL] 未找到 mvn（Maven），请安装并加入 PATH。 & pause & exit /b 1)
where curl >nul 2>nul || (echo [FAIL] 未找到 curl。 & pause & exit /b 1)
echo [OK]   java / mvn / curl 就绪
if not exist "%SCRIPT_DIR%\logs" mkdir "%SCRIPT_DIR%\logs"
if not exist "%RUN_DIR%" mkdir "%RUN_DIR%"
goto :eof

:autorestart_check
set "NEED_STOP=0"
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul 2>nul && set "NEED_STOP=1"
if exist "%PID_BACKEND%" (
  for /f %%p in (%PID_BACKEND%) do tasklist /fi "PID eq %%p" | find "%%p" >nul 2>nul && set "NEED_STOP=1"
)
if "%NEED_STOP%"=="1" (
  echo [WARN] 检测到后端已在运行，将先关闭再重启（自动重启）...
  call :stop_service
)
goto :eof

:stop_service
if exist "%PID_FRONTEND%" for /f %%p in (%PID_FRONTEND%) do taskkill /PID %%p /F >nul 2>nul && echo   前端进程 (PID %%p^) 已发送终止信号
if exist "%PID_BACKEND%"  for /f %%p in (%PID_BACKEND%)  do taskkill /PID %%p /F >nul 2>nul && echo   后端进程 (PID %%p^) 已发送终止信号
timeout /t 2 /nobreak >nul
call :kill_port %FRONTEND_PORT%
call :kill_port %BACKEND_PORT%
if exist "%PID_BACKEND%"  del /q "%PID_BACKEND%"  >nul 2>nul
if exist "%PID_FRONTEND%" del /q "%PID_FRONTEND%" >nul 2>nul
goto :eof

:kill_port
set "p=%1"
netstat -ano | findstr ":%p% " | findstr "LISTENING" >nul 2>nul || (echo   端口 %p%：无运行中的服务（已干净） & goto :eof)
echo   端口 %p%：发现监听进程，正在终止...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%p% " ^| findstr "LISTENING"') do taskkill /PID %%a /F >nul 2>nul
timeout /t 1 /nobreak >nul
netstat -ano | findstr ":%p% " | findstr "LISTENING" >nul 2>nul && (echo   端口 %p% 仍被占用，请手动检查释放。 ) || echo   端口 %p% 已释放
goto :eof

:mysql_check
echo [INFO] 检查 MySQL（仅警告）...
where mysqladmin >nul 2>nul && (
  mysqladmin ping >nul 2>nul && echo [OK]   MySQL 可连通 || echo [WARN] MySQL 未启动：应用可启动，但涉及库的功能会报错。请先启动 MySQL 并执行 sql\ssm_learn.sql
) || echo [WARN] 未检测到 mysqladmin，请确保 MySQL 已启动并执行过 sql\ssm_learn.sql
goto :eof

:compile
echo [INFO] 编译项目（mvn compile）...
mvn -B -q compile > "%BACKEND_LOG%" 2>&1
if errorlevel 1 (
  echo [FAIL] Maven 编译失败，查看日志： type logs\backend.log
  pause
  exit /b 1
)
echo [OK]   编译成功
goto :eof

:start_backend
echo [INFO] 启动后端（SSM + 嵌入式 Jetty^) -^> %BACKEND_URL%
set "WD=%SCRIPT_DIR%"
for /f "delims=" %%p in ('powershell -NoProfile -Command "$env:WD='%WD%'; (Start-Process -FilePath mvn.cmd -ArgumentList '-B','jetty:run' -RedirectStandardOutput (Join-Path $env:WD backend.log) -RedirectStandardError (Join-Path $env:WD backend.log) -WorkingDirectory $env:WD -PassThru).Id"') do set "BACKEND_PID=%%p"
echo [OK]   后端进程已拉起 (PID %BACKEND_PID%)
echo %BACKEND_PID%> "%PID_BACKEND%"
echo [INFO] 等待后端就绪（最长 %MAX_WAIT%s）...
set /a "i=0"
:wait_backend_loop
if %i% geq %MAX_WAIT% goto backend_timeout
for /f "delims=" %%c in ('curl -s -o nul -w "%%{http_code}" "%BACKEND_URL%"') do set "code=%%c"
if "%code%"=="200" goto backend_ok
if "%code%"=="302" goto backend_ok
if "%code%"=="301" goto backend_ok
timeout /t 2 /nobreak >nul
set /a "i+=2"
goto wait_backend_loop
:backend_ok
echo [OK]   后端就绪：%BACKEND_URL%
goto :eof
:backend_timeout
echo [FAIL] 后端启动超时。最近 25 行日志：
powershell -NoProfile -Command "Get-Content '%BACKEND_LOG%' -Tail 25"
if exist "%PID_BACKEND%" for /f %%p in (%PID_BACKEND%) do taskkill /PID %%p /F >nul 2>nul
pause
exit /b 1

:start_frontend
where node >nul 2>nul || (echo [WARN] 未检测到 node，跳过独立前端预览服务。页面经后端访问：%BACKEND_URL% & goto :eof)
netstat -ano | findstr ":%FRONTEND_PORT% " | findstr "LISTENING" >nul 2>nul && (
  echo [WARN] 端口 %FRONTEND_PORT% 已被占用，跳过独立前端预览服务。页面经后端访问：%BACKEND_URL%
  goto :eof
)
echo [INFO] 启动前端（Layui 预览服务^) -^> %FRONTEND_URL%
set "WD=%SCRIPT_DIR%"
for /f "delims=" %%p in ('powershell -NoProfile -Command "$env:WD='%WD%'; (Start-Process -FilePath node -ArgumentList (Join-Path $env:WD 'scripts\frontend-dev-server.js') -RedirectStandardOutput (Join-Path $env:WD frontend.log) -RedirectStandardError (Join-Path $env:WD frontend.log) -WorkingDirectory $env:WD -PassThru).Id"') do set "FRONTEND_PID=%%p"
echo %FRONTEND_PID%> "%PID_FRONTEND%"
set /a "i=0"
:wait_frontend_loop
if %i% geq 30 goto frontend_done
for /f "delims=" %%c in ('curl -s -o nul -w "%%{http_code}" "%FRONTEND_URL%"') do set "fcode=%%c"
if "%fcode%"=="200" goto frontend_ok
timeout /t 1 /nobreak >nul
set /a "i+=1"
goto wait_frontend_loop
:frontend_ok
echo [OK]   前端就绪：%FRONTEND_URL%
goto :eof
:frontend_done
goto :eof

:status
echo.
echo ================ 服务状态 ================
echo   [运行中] 后端 (SSM API)    : %BACKEND_URL%
if exist "%PID_FRONTEND%" (
  echo   [运行中] 前端 (Layui 预览) : %FRONTEND_URL%  (Node 静态服务 + API 反代)
) else (
  echo   [未启动] 前端 (Layui 预览) : 页面经后端访问 %BACKEND_URL%
)
echo ----------------------------------------------
echo   后端日志 : type logs\backend.log
echo   前端日志 : type logs\frontend.log
echo   一键关闭 : stop.bat
echo ============================================
goto :eof
