#!/bin/bash
# spring-transaction-boundary-lab 重启（macOS）— 等价于 ./start.command restart <target>
# 双击：重启全部（后端 + 本脚本拉起的中间件）。命令行： ./restart.command [all|backend|middleware] [-y]
exec "$(cd "$(dirname "$0")"; pwd)/start.command" restart "$@"
