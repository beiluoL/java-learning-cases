@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
REM ============================================================
REM  Spring Vue Learn 一键启动 / 重启（Windows）
REM  双击：默认 start all（已运行则自动重启）。
REM  命令行： start.bat [start|restart] [all|backend|frontend] [-y]
REM  后端：Maven spring-boot:run :8080   前端：Vite dev :5173
REM ============================================================
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
cd /d "%SCRIPT_DIR%" || exit /b 1

set "PROJECT_NAME=Spring Vue Learn 学习框架"
set "BACKEND_PORT=8080"
set "FRONTEND_PORT=5173"
set "BACKEND_MAVEN_GOAL=spring-boot:run"
set "BACKEND_HEALTH_PATH=/api/health"
set "BACKEND_COMPILE_CMD=true"
set "MYSQL_SQL_PATH="
set "WEBAPP_DIR=frontend"
set "BACKEND_HOST=127.0.0.1"
set "BACKEND_LOG=%SCRIPT_DIR%\logs\backend.log"
set "FRONTEND_LOG=%SCRIPT_DIR%\logs\frontend.log"
set "RUN_DIR=%SCRIPT_DIR%\.run"
set "PID_BACKEND=%RUN_DIR%\backend.pid"
set "PID_FRONTEND=%RUN_DIR%\frontend.pid"
set "BACKEND_URL=http://localhost:%BACKEND_PORT%/"
set "HEALTH_URL=http://localhost:%BACKEND_PORT%%BACKEND_HEALTH_PATH%"
set "FRONTEND_URL=http://localhost:%FRONTEND_PORT%/"
set "MAX_WAIT=150"

REM ---- 参数解析 ----
set "ACTION=start"
set "TARGET=all"
:parse_args
if "%1"=="" goto :done_parse
if /i "%1"=="start"      set "ACTION=start"
if /i "%1"=="restart"    set "ACTION=restart"
if /i "%1"=="all"        set "TARGET=all"
if /i "%1"=="backend"    set "TARGET=backend"
if /i "%1"=="frontend"   set "TARGET=frontend"
if /i "%1"=="middleware" set "TARGET=middleware"
if /i "%1"=="-y"         set "YES=1"
shift
goto :parse_args
:done_parse

call :preflight

echo ================ %PROJECT_NAME% %ACTION% (目标: %TARGET%) ================

if /i "%TARGET%"=="middleware" call :do_middleware
if /i "%TARGET%"=="all"        call :do_middleware
if /i "%TARGET%"=="backend"    call :do_backend
if /i "%TARGET%"=="all"        call :do_backend
if /i "%TARGET%"=="frontend"   call :do_frontend
if /i "%TARGET%"=="all"        call :do_frontend

call :status
echo.
echo 服务已在后台运行。按任意键关闭本窗口（服务不中断，停止请用 stop.bat）...
pause >nul
exit /b 0

REM ============ 子程序 ============
:preflight
echo [INFO] 检查运行环境（%PROJECT_NAME%）...
where java >nul 2>nul || (echo [FAIL] 未找到 java（JDK 17），请安装 JDK 并加入 PATH。 & pause & exit /b 1)
where mvn  >nul 2>nul || (echo [FAIL] 未找到 mvn（Maven），请安装并加入 PATH。 & pause & exit /b 1)
where curl >nul 2>nul || (echo [FAIL] 未找到 curl。 & pause & exit /b 1)
echo [ OK ]  java / mvn / curl 就绪
if not exist "%SCRIPT_DIR%\logs" mkdir "%SCRIPT_DIR%\logs"
if not exist "%RUN_DIR%" mkdir "%RUN_DIR%"
goto :eof

:do_middleware
goto :eof

:do_backend
if /i "%ACTION%"=="restart" call :stop_backend
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul 2>nul && (echo [WARN] 检测到后端已在运行，自动重启... & call :stop_backend)
if exist "%PID_BACKEND%" ( for /f %%p in (%PID_BACKEND%) do tasklist /fi "PID eq %%p" | find "%%p" >nul 2>nul && (echo [WARN] 检测到后端已在运行，自动重启... & call :stop_backend) )
call :compile
if errorlevel 1 exit /b 1
call :start_backend
goto :eof

:do_frontend
if /i "%ACTION%"=="restart" call :stop_frontend
netstat -ano | findstr ":%FRONTEND_PORT% " | findstr "LISTENING" >nul 2>nul && (echo [WARN] 端口 %FRONTEND_PORT% 已被占用，跳过独立前端启动。页面经后端访问：%BACKEND_URL% & goto :eof)
call :start_frontend
goto :eof

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

:compile
if "%BACKEND_COMPILE_CMD%"=="true" goto :eof
echo [INFO] 编译项目（%BACKEND_COMPILE_CMD%）...
%BACKEND_COMPILE_CMD% > "%BACKEND_LOG%" 2>&1
if errorlevel 1 (echo [FAIL] 编译失败，查看日志： type logs\backend.log & pause & exit /b 1)
echo [ OK ]  编译成功
goto :eof

:start_backend
echo [INFO] 启动后端（Maven %BACKEND_MAVEN_GOAL%^) -^> %BACKEND_URL%
set "WD=%SCRIPT_DIR%"
for /f "delims=" %%p in ('powershell -NoProfile -Command "$env:WD='%WD%'; (Start-Process -FilePath mvn.cmd -ArgumentList '-B','-f','backend/pom.xml','%BACKEND_MAVEN_GOAL%' -RedirectStandardOutput (Join-Path $env:WD backend.log) -RedirectStandardError (Join-Path $env:WD backend.log) -WorkingDirectory $env:WD -PassThru).Id"') do set "BACKEND_PID=%%p"
echo [ OK ]  后端进程已拉起 (PID %BACKEND_PID%)
echo %BACKEND_PID%> "%PID_BACKEND%"
echo [INFO] 等待后端就绪（最长 %MAX_WAIT%s）...
set /a "i=0"
:wait_backend_loop
if %i% geq %MAX_WAIT% goto backend_timeout
for /f "delims=" %%c in ('curl -s -o nul -w "%%{http_code}" "%HEALTH_URL%"') do set "code=%%c"
if "%code%"=="200" goto backend_ok
if "%code%"=="302" goto backend_ok
if "%code%"=="301" goto backend_ok
timeout /t 2 /nobreak >nul
set /a "i+=2"
goto wait_backend_loop
:backend_ok
echo [ OK ]  后端就绪：%BACKEND_URL%
goto :eof
:backend_timeout
echo [FAIL] 后端启动超时（请确认 MySQL 已启动且账号密码正确^)。最近 25 行日志：
powershell -NoProfile -Command "Get-Content '%BACKEND_LOG%' -Tail 25"
if exist "%PID_BACKEND%" for /f %%p in (%PID_BACKEND%) do taskkill /PID %%p /F >nul 2>nul
pause
exit /b 1

:start_frontend
where node >nul 2>nul || (echo [WARN] 未检测到 node，跳过前端启动（请手动 cd frontend 后 npm install ^&^& npm run dev^)。 & goto :eof)
where npm >nul 2>nul || (echo [WARN] 未检测到 npm，跳过前端启动。 & goto :eof)
if not exist "%SCRIPT_DIR%\frontend\node_modules" (
  echo [INFO] 首次运行：安装前端依赖（npm install^)...
  powershell -NoProfile -Command "Set-Location '%SCRIPT_DIR%\frontend'; npm install" >> "%SCRIPT_DIR%\logs\frontend.log" 2>&1 || (echo [WARN] 前端依赖安装失败，请手动 cd frontend 后 npm install。 & goto :eof)
)
echo [INFO] 启动前端（Vite dev :%FRONTEND_PORT%^) -^> %FRONTEND_URL%
for /f "delims=" %%p in ('powershell -NoProfile -Command "(Start-Process -FilePath npm -ArgumentList 'run','dev' -RedirectStandardOutput '%SCRIPT_DIR%\logs\frontend.log' -RedirectStandardError '%SCRIPT_DIR%\logs\frontend.log' -WorkingDirectory '%SCRIPT_DIR%\frontend' -PassThru).Id"') do set "FRONTEND_PID=%%p"
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
echo [ OK ]  前端就绪：%FRONTEND_URL%
goto :eof
:frontend_done
echo [WARN] 前端未就绪（可能仍在安装依赖/编译^)。日志： type logs\frontend.log
goto :eof

:status
echo.
echo ================ %PROJECT_NAME% 服务状态 ================
netstat -ano | findstr ":%BACKEND_PORT% " | findstr "LISTENING" >nul 2>nul && echo   [运行中] 后端 (API)         : %BACKEND_URL% || echo   [未运行] 后端 (API)
if exist "%PID_FRONTEND%" ( echo   [运行中] 前端 (预览)        : %FRONTEND_URL% ) else ( echo   [未启动] 前端 (预览)        : 请检查日志 logs\frontend.log )
echo ----------------------------------------------
echo   后端日志 : type logs\backend.log
echo   前端日志 : type logs\frontend.log
echo   一键关闭 : stop.bat   (重启: restart.bat)
echo ============================================
goto :eof
