@echo off
chcp 65001 >nul
setlocal
set BACKEND_PORT=8080
set BACKEND_SIG=DemoApplication
echo ====================================================
echo   spring-ai-milvus-demo 一键停止
echo ====================================================
echo 关闭后端进程并释放端口 %BACKEND_PORT% ...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :%BACKEND_PORT% ^| findstr LISTENING') do taskkill /PID %%a /F >nul 2>&1
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%DemoApplication%%'" get ProcessId 2^>nul ^| findstr [0-9]') do taskkill /PID %%p /F >nul 2>&1
echo 完成。
pause
