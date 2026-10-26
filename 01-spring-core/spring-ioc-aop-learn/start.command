#!/bin/bash
# ============================================================
#  spring-ioc-aop-learn · 一键运行 (macOS) · 控制台程序
#  双击运行：先关闭同类进程，再编译并执行手写迷你 Spring 示例
# ============================================================
set -e
BACKEND_SIG="com.learn.springmini.demo.MainApplication"
PROJECT_DIR="$(cd "$(dirname "$0")"; pwd)"

JDK="/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
[ -d "$JDK" ] && export JAVA_HOME="$JDK"
MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"
[ -x "$MVN_BIN" ] && MVN="$MVN_BIN" || MVN="mvn"

echo "===================================================="
echo "  spring-ioc-aop-learn 一键运行（控制台）"
echo "===================================================="
echo "[1/2] 关闭已运行的同类进程 ..."
pkill -f "$BACKEND_SIG" 2>/dev/null || true
sleep 1
echo "[2/2] 编译并运行 (mvn compile exec:java) ..."
echo "----------------------------------------------------"
cd "$PROJECT_DIR"
"$MVN" compile exec:java
echo "----------------------------------------------------"
echo ""
echo "运行结束。按任意键退出..."
read -n 1 -s -r
