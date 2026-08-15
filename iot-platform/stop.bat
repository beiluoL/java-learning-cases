@echo off
chcp 65001 >nul
REM ============================================================================
REM  IoT 大数据平台 - 一键关闭（Windows 双击运行）
REM  按端口精准停止后端(8080)与前端(5173)，不误杀其他程序。
REM ============================================================================
echo 正在停止 IoT 大数据平台...

REM 停止后端（监听 8080）
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
  taskkill /f /pid %%a >nul 2>&1
  echo 后端已停止 (PID: %%a)
)

REM 停止前端（监听 5173）
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173" ^| findstr "LISTENING"') do (
  taskkill /f /pid %%a >nul 2>&1
  echo 前端已停止 (PID: %%a)
)

echo 全部服务已停止。
pause
