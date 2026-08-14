@echo off
chcp 65001 >nul
setlocal
set BACKEND_SIG=MilvusJavaDemo
echo ====================================================
echo   milvus-java-minimal-demo 一键停止
echo ====================================================
echo 关闭运行中的进程 ...
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%MilvusJavaDemo%%'" get ProcessId 2^>nul ^| findstr [0-9]') do taskkill /PID %%p /F >nul 2>&1
echo 完成。
pause
