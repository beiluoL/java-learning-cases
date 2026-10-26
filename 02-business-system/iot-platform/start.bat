@echo off
chcp 65001 >nul
REM ============================================================================
REM  IoT 大数据平台 - 一键启动（Windows 双击运行）
REM  拉起后端(Spring Boot)与前端(Vite)，各开独立窗口并保持日志。
REM  关闭请运行 stop.bat。
REM ============================================================================
cd /d %~dp0
if not exist logs mkdir logs

echo ==================================================
echo   IoT 大数据平台 启动中...
echo   后端: http://localhost:8080  前端: http://localhost:5173
echo ==================================================

REM ---------- 后端 ----------
if not exist backend\iot-web\target\iot-web-1.0.0.jar (
  echo [1/2] 未检测到后端 jar，开始编译（首次较慢）...
  cd backend
  call mvn -q -DskipTests package
  if errorlevel 1 (
    echo 后端编译失败，请检查 Maven 环境
    pause
    exit /b 1
  )
  cd /d %~dp0
)
echo [1/2] 启动后端 Spring Boot...
start "IoT-Backend" cmd /k "cd /d %~dp0backend\iot-web && java -jar target\iot-web-1.0.0.jar"

REM ---------- 前端 ----------
echo [2/2] 准备前端依赖...
cd /d %~dp0frontend
if not exist node_modules call npm install
if errorlevel 1 (
  echo 前端依赖安装失败，请检查 Node 环境
  pause
  exit /b 1
)
echo [2/2] 启动前端 Vite...
start "IoT-Frontend" cmd /k "cd /d %~dp0frontend && npm run dev"

echo.
echo 启动完成！浏览器访问 http://localhost:5173
echo 后端日志: logs\backend.log   前端日志: logs\frontend.log
echo 关闭请运行 stop.bat。
pause
