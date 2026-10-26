@echo off
chcp 65001 >nul
setlocal
set BACKEND_PORT=8080
set FRONTEND_PORT=8010
set BACKEND_SIG=RagDocQaApplication
echo ====================================================
echo   rag-doc-qa-system 一键停止
echo ====================================================
echo 关闭前后端进程并释放端口 (%BACKEND_PORT%/%FRONTEND_PORT%) ...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :%BACKEND_PORT% ^| findstr LISTENING') do taskkill /PID %%a /F >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :%FRONTEND_PORT% ^| findstr LISTENING') do taskkill /PID %%a /F >nul 2>&1
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%RagDocQaApplication%%'" get ProcessId 2^>nul ^| findstr [0-9]') do taskkill /PID %%p /F >nul 2>&1
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='node.exe' and CommandLine like '%%vite%%'" get ProcessId 2^>nul ^| findstr [0-9]') do taskkill /PID %%p /F >nul 2>&1
echo 完成。
pause
