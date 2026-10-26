@echo off
chcp 65001 >nul
REM =============================================================================
REM  智能客服 Agent —— 一键启动 + 真实流式校验脚本（Windows）
REM  用法：双击本文件，或在 cmd 中  verify.bat
REM  会按需启动 agent-bridge(:3001) + 后端(:8080) + 前端(:5173)，
REM  然后向 /api/chat/stream 发测试消息，校验逐 token 流式并判定运行模式。
REM =============================================================================
setlocal EnableDelayedExpansion

set BRIDGE_PORT=3001
set BACKEND_PORT=8080
set FRONTEND_PORT=5173
set BRIDGE_URL=http://localhost:%BRIDGE_PORT%
set BACKEND_URL=http://localhost:%BACKEND_PORT%

cd /d "%~dp0"

REM ---- 工具探测 ----
where java >nul 2>&1 || (echo [X] 未找到 java，请安装 JDK 17+ & goto :fail)
where npm >nul 2>&1 || (echo [X] 未找到 npm，请安装 Node 18+ & goto :fail)
for /f "delims=" %%i in ('where mvn 2^>nul') do set MVN_BIN=%%i
if not defined MVN_BIN (echo [!] 未检测到 maven（jar 已存在时无需构建）)

REM ---- 1. 启动 agent-bridge ----
echo.
echo == agent-bridge (CodeBuddy 桥接 :%BRIDGE_PORT%) ==
curl -s -o nul -m 2 %BRIDGE_URL%/health >nul 2>&1
if errorlevel 1 (
  cd agent-bridge
  if not exist node_modules (echo 桥接依赖缺失，执行 npm install ... & call npm install)
  if not exist node_modules\@tencent-ai\codebuddy-code\bin\codebuddy (echo 补装 codebuddy-code CLI ... & call npm install @tencent-ai/codebuddy-code@latest >nul 2>&1)
  start "bridge" /b cmd /c "npm start > bridge.log 2>&1"
  cd ..
  echo 桥接已启动（日志 agent-bridge/bridge.log）
) else (
  echo 端口 %BRIDGE_PORT% 已占用，复用已有桥接
)

REM ---- 2. 启动后端 ----
echo.
echo == 后端 (Spring Boot :%BACKEND_PORT%) ==
curl -s -o nul -m 2 %BACKEND_URL%/api/health >nul 2>&1
if errorlevel 1 (
  cd backend
  set "JAR="
  for %%f in (target\*.jar) do set "JAR=%%f"
  if not defined JAR (
    if defined MVN_BIN (echo 后端 jar 缺失，构建中 ... & call "%MVN_BIN%" package -DskipTests -q) else (echo [X] 未找到 jar 且未安装 maven & goto :fail)
    for %%f in (target\*.jar) do set "JAR=%%f"
  )
  if not defined JAR (echo [X] 仍未找到 jar & goto :fail)
  start "backend" /b cmd /c "java -Dserver.port=%BACKEND_PORT% -jar %JAR% > run.log 2>&1"
  cd ..
  echo 后端已启动（日志 backend/run.log）
) else (
  echo 端口 %BACKEND_PORT% 已占用，复用已有后端
)

REM ---- 3. 启动前端 ----
echo.
echo == 前端 (Vue :%FRONTEND_PORT%) ==
curl -s -o nul -m 2 http://localhost:%FRONTEND_PORT%/ >nul 2>&1
if errorlevel 1 (
  cd frontend
  if not exist node_modules (echo 前端依赖缺失，执行 npm install ... & call npm install)
  start "frontend" /b cmd /c "npm run dev -- --port %FRONTEND_PORT% --host > frontend.log 2>&1"
  cd ..
  echo 前端已启动（日志 frontend/frontend.log）
) else (
  echo 端口 %FRONTEND_PORT% 已占用，复用已有前端
)

REM ---- 4. 等待就绪 ----
echo.
echo == 等待后端就绪 ==
set /a n=0
:waitloop
curl -s -o nul -m 2 %BACKEND_URL%/api/health >nul 2>&1
if not errorlevel 1 goto :ready
set /a n+=1
if %n% geq 60 (echo [X] 后端启动超时，请查看 backend/run.log & goto :fail)
timeout /t 1 >nul
goto :waitloop
:ready
echo 后端已就绪。

REM ---- 5. 核心校验 ----
echo.
echo == 核心校验：CodeBuddy 流式链路 ==
set TEST_MSG=我要申请退款怎么操作
curl -sN -m 45 -X POST %BACKEND_URL%/api/chat/stream -H "Content-Type: application/json" -d "{\"message\":\"%TEST_MSG%\"}" > sse_tmp.txt 2>nul

set TOKEN_CNT=0
for /f %%a in ('find /c "type"":"text"" sse_tmp.txt') do set TOKEN_CNT=%%a
findstr /c:"\"type\":\"done\"" sse_tmp.txt >nul 2>&1 && set GOT_DONE=1 || set GOT_DONE=0
findstr /c:"\"type\":\"error\"" sse_tmp.txt >nul 2>&1 && set BRIDGE_ERR=1 || set BRIDGE_ERR=0

echo 收到 text token 事件数 : %TOKEN_CNT%
if %GOT_DONE%==1 (echo [v] 收到 done 事件（流式正常结束）) else (echo [!] 未收到 done 事件)

if %TOKEN_CNT% gtr 0 (echo [v] 确认是逐 token 流式输出) else (echo [X] 未检测到逐 token 输出 & goto :fail)

echo.
echo -- 运行模式 --
if %BRIDGE_ERR%==0 (
  echo [v] 当前走【真实 CodeBuddy 大模型】逐 token 流式
  echo [v] 结论: PASS（端到端真实流式已跑通）
) else (
  set REASON=桥接返回 error（详见 agent-bridge/bridge.log）
  findstr /i "Sensitive model request protection" agent-bridge/bridge.log >nul 2>&1 && set REASON=企业 DLP 数据防护网关不可达，SDK 调用被拦截（受限/离线环境常见）
  findstr /i "调用超时" agent-bridge/bridge.log >nul 2>&1 && set REASON=CodeBuddy 调用超时
  echo [!] 当前走【本地规则引擎降级】—— 仍可逐 token 流式，但不是 CodeBuddy 真模型
  echo     降级原因: !REASON!
  echo     （本机且 DLP 服务可达、登录态/KEY 可用时会切换为真实 CodeBuddy 流式）
  echo [!] 结论: 链路 PASS（流式可用）；模型模式 = 本地降级（非阻断）
)

echo.
echo -- 访问地址 --
echo 前端（客服/管理）: http://localhost:%FRONTEND_PORT%/
echo 后端健康        : %BACKEND_URL%/api/health
echo 桥接健康        : %BRIDGE_URL%/health
echo.
echo 校验完成。服务仍在后台运行；用 stop.bat 停止。
del /q sse_tmp.txt >nul 2>&1
goto :eof

:fail
echo.
echo [X] 校验未通过，请查看上方日志与 backend/run.log、agent-bridge/bridge.log。
del /q sse_tmp.txt >nul 2>&1
exit /b 1
