@echo off
REM ===========================================================================
REM  customer-service-agent 一键启动（前端 + 后端）  [Windows 双击运行]
REM  流程：① 关闭已运行同类服务并释放端口 ② 启动后端 ③ 启动前端
REM  访问：前端 http://localhost:5173/   后端 http://localhost:8080/api
REM ===========================================================================
setlocal
set NAME=customer-service-agent
set BSIGN=com.example.csagent.CsAgentApplication
set BPORT=8080
set BDIR=backend
set FDIR=frontend
set FPORT=5173

echo [%NAME%] 第 1 步：关闭已运行的同类服务，释放端口 %BPORT% / %FPORT% ...
wmic process where "CommandLine like '%%BSIGN%%'" call terminate >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%BPORT% ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%FPORT% ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
timeout /t 2 >nul

echo [%NAME%] 第 2 步：启动后端 (端口 %BPORT%) ...
start "backend-%NAME%" cmd /k "cd /d %~dp0%BDIR% && mvn spring-boot:run"

echo [%NAME%] 等待后端就绪 ...
:waitb
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%BPORT% ^| findstr LISTENING') do goto backendup
timeout /t 2 >nul
goto waitb
:backendup
echo [%NAME%] 后端已就绪。

echo [%NAME%] 第 3 步：启动前端 (端口 %FPORT%) ...
cd /d %~dp0%FDIR%
if not exist node_modules ( call npm install )
call npm run dev -- --port %FPORT% --host
