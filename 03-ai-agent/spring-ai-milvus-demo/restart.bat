@echo off
chcp 65001 >nul
setlocal
set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=all"
echo ====================================================
echo   AI Token Cost Platform - 重启 (%TARGET%)
echo ====================================================
call "%~dp0stop.bat" -y
call "%~dp0start.bat" %TARGET%
