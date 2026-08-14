@echo off
REM ===========================================================================
REM  customer-service-agent 一键关闭（前端 + 后端）  [Windows 双击运行]
REM ===========================================================================
setlocal
set NAME=customer-service-agent
set BSIGN=com.example.csagent.CsAgentApplication
set BPORT=8080
set FPORT=5173

echo [%NAME%] 正在停止服务 ...
wmic process where "CommandLine like '%%BSIGN%%'" call terminate >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%BPORT% ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%FPORT% ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
echo [%NAME%] 已停止，端口已释放。
pause
