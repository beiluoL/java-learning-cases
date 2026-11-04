#!/usr/bin/env bash
# 一键跑全部压测场景并生成监控报告（需先启动 Redis 与后端 8080）
set -e
cd "$(dirname "$0")"

HOST="${1:-http://localhost:8080}"
CONC="${2:-50}"
REQ="${3:-600}"

echo ">> 目标后端: $HOST  并发: $CONC  请求数/场景: $REQ"
python3 run_benchmark.py --scenario all --host "$HOST" --concurrency "$CONC" --requests "$REQ"
echo ">> 报告已生成在 report/ 目录，直接用浏览器打开 HTML 即可。"
