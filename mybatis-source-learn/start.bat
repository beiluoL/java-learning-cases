@echo off
chcp 65001 >nul
setlocal
set BACKEND_SIG=com.learn.mybatismini.demo.MainApplication
set PROJECT_DIR=%~dp0

echo ====================================================
echo   mybatis-source-learn 一键运行（控制台）
echo ====================================================
echo [1/2] 关闭已运行进程 ...
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%com.learn.mybatismini.demo.MainApplication%%'" get ProcessId 2^>nul ^| findstr [0-9]') do taskkill /PID %%p /F >nul 2>&1

echo [2/2] 编译并运行 ...
cd /d %PROJECT_DIR%
mvn compile exec:java
echo 运行结束。
pause
