@echo off
chcp 65001 >nul
REM ============================================================
REM FinPay 一键启动（Windows 双击运行）
REM 拉起：业务服务(8080) + 网关(8088) + Vue 前端(5173)
REM 端口/进程自校验；若已运行则先停后起（自动重启）
REM ============================================================
setlocal
set "DIR=%~dp0.."
set "RUN_DIR=%DIR%scripts\.run"
if not exist "%RUN_DIR%" mkdir "%RUN_DIR%"

set "MVN=mvn"
set "NPM=npm"
set "JAVA_PORTS=8080 8088"
set "FE_PORT=5173"

echo [INFO] 项目根目录: %DIR%

REM 端口占用检测（PowerShell 辅助）
:stop_existing
echo [INFO] 检测并清理已运行实例...
for %%p in (%JAVA_PORTS% %FE_PORT%) do (
  for /f "tokens=5" %%a in ('netstat -ano ^| findstr "LISTENING" ^| findstr ":%%p "') do (
    echo [WARN] 端口 %%p 被 PID %%a 占用，正在停止...
    taskkill /PID %%a /F >nul 2>&1
  )
)
REM 兜底清理 PID 文件
for %%f in ("%RUN_DIR%\*.pid") do (
  if exist "%%f" (
    for /f %%a in (%%f) do taskkill /PID %%a /F >nul 2>&1
    del /f "%%f" >nul 2>&1
  )
)

REM 前置依赖检查
where %MVN% >nul 2>&1
if errorlevel 1 ( echo [FAIL] 未找到 mvn，请安装 Maven 并加入 PATH & pause & exit /b 1 )
where %NPM% >nul 2>&1
if errorlevel 1 ( echo [FAIL] 未找到 npm，请安装 Node.js & pause & exit /b 1 )

echo [INFO] 启动业务服务 (finpay-business :8080) ...
start "finpay-business" cmd /c "cd /d %DIR% && %MVN% -q -pl finpay-business -am spring-boot:run > %RUN_DIR%\business.log 2>&1"
echo [INFO] 启动网关 (finpay-gateway :8088) ...
start "finpay-gateway" cmd /c "cd /d %DIR% && %MVN% -q -pl finpay-gateway -am spring-boot:run > %RUN_DIR%\gateway.log 2>&1"

echo [INFO] 启动前端 (finpay-web :5173) ...
cd /d %DIR%\finpay-web
if not exist node_modules (
  echo [INFO] 首次运行，安装前端依赖 (npm install) ...
  call %NPM% install
  if errorlevel 1 ( echo [FAIL] 前端依赖安装失败 & pause & exit /b 1 )
)
start "finpay-web" cmd /c "cd /d %DIR%\finpay-web && %NPM% run dev > %RUN_DIR%\web.log 2>&1"

echo.
echo [ OK ] 已全部启动（独立窗口运行，可最小化）
echo ---------------------------------------------------
echo   管理后台:  http://localhost:5173  (账号 admin / admin123)
echo   接口文档:  http://localhost:8080/doc.html
echo   业务 API:  http://localhost:8080
echo   网关 API:  http://localhost:8088
echo ---------------------------------------------------
echo   日志目录:  %RUN_DIR%
echo   停止脚本:  双击 scripts\stop.bat
pause
endlocal
