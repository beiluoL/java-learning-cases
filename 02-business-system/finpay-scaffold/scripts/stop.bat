@echo off
chcp 65001 >nul
REM ============================================================
REM FinPay 一键停止（Windows 双击运行）
REM 精准停止本项目拉起的后端/前端进程（依据 PID 文件），不误杀其他进程
REM ============================================================
setlocal
set "DIR=%~dp0.."
set "RUN_DIR=%DIR%scripts\.run"

echo [INFO] 停止 FinPay 相关进程...

for %%f in ("%RUN_DIR%\business.pid" "%RUN_DIR%\gateway.pid" "%RUN_DIR%\web.pid") do (
  if exist "%%f" (
    for /f %%a in (%%f) do (
      echo [INFO] 停止 PID %%a
      taskkill /PID %%a /F >nul 2>&1
    )
    del /f "%%f" >nul 2>&1
  )
)

REM 兜底：清理仍占用端口的残留进程（仅本项目的 8080/8088/5173）
for %%p in (8080 8088 5173) do (
  for /f "tokens=5" %%a in ('netstat -ano ^| findstr "LISTENING" ^| findstr ":%%p "') do (
    echo [WARN] 端口 %%p 仍有进程 %%a，强制停止
    taskkill /PID %%a /F >nul 2>&1
  )
)

echo [ OK ] FinPay 已停止
pause
endlocal
