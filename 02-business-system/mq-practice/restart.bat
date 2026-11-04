@echo off
chcp 65001 >nul
rem =====================================================================================
rem  MQ 消息队列实战 · 一键重启（Windows 双击运行）
rem
rem  双击   → 重启全部（先 stop，再 start）
rem  命令行 → restart.bat [all^|backend^|frontend^|middleware]
rem           restart.bat backend   仅重启后端（不碰中间件，最快）
rem =====================================================================================
setlocal
cd /d "%~dp0"

set TARGET=all
if /i "%~1"=="backend" set TARGET=backend
if /i "%~1"=="frontend" set TARGET=frontend
if /i "%~1"=="middleware" set TARGET=middleware
if /i "%~1"=="all" set TARGET=all

echo ^>^>^> 停止（目标: %TARGET%）...
call stop.bat %TARGET% --silent
echo.
echo ^>^>^> 启动（目标: %TARGET%）...
call start.bat start %TARGET%
