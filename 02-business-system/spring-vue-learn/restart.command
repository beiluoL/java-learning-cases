#!/usr/bin/env bash
# Spring Vue Learn 重启（macOS）— 等价于 ./start.command restart <target>
# 双击：重启全部（后端 + 前端）。命令行： ./restart.command [all|backend|frontend] [-y]
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/start.command" restart "$@"
