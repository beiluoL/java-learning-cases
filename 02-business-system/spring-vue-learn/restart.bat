@echo off
chcp 65001 >nul
REM Spring Vue Learn 重启（Windows）— 等价于 start.bat restart <target>
call "%~dp0start.bat" restart %*
