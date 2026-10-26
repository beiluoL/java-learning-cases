#!/bin/bash
BACKEND_SIG="MilvusJavaDemo"
echo "===================================================="
echo "  milvus-java-minimal-demo 一键停止"
echo "===================================================="
echo "关闭运行中的进程 ..."
pkill -f "$BACKEND_SIG" 2>/dev/null && echo "  ✓ 已终止进程" || echo "  · 无运行中的进程"
read -n 1 -s -r -p "按任意键退出..."
