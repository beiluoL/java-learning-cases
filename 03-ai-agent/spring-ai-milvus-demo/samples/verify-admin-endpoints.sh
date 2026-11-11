#!/usr/bin/env bash
#
# 管理接口回归脚本
#
# 用途     ：逐个调用全部只读管理端点 + 可安全执行的操作端点，断言响应 code=0
# 用法     ：bash samples/verify-admin-endpoints.sh
# 参数     ：无
# 环境变量 ：BASE（默认 http://127.0.0.1:8091），可指向其他端口实例
# 执行前提 ：后端已启动；MySQL 容器 healthy（多数端点会查库）；Redis 可用
# 副作用   ：基本只读。3 个 POST 中 evaluate / anomaly-scan 只做评估会写告警记录，
#            daily-summary/rebuild 为幂等重算，可重复执行
# 失败处理 ：任一端点失败会打印路径与原始响应片段，并计入失败数；
#            脚本最后以「失败数」决定退出码（0=全通过，1=有失败），可直接用于门禁
#
# 注意     ：不使用 set -e —— 需要跑完全部端点再统一汇总，单点失败不应中断。
#
set -uo pipefail

BASE=${BASE:-http://127.0.0.1:8091}
OK=0
BAD=0
FAILED=""

hit() {
  local method="$1" path="$2" body
  if [ "$method" = GET ]; then
    body=$(curl -s --noproxy '*' -m 15 "$BASE$path")
  else
    body=$(curl -s --noproxy '*' -m 40 -X POST "$BASE$path" -H 'Content-Type: application/json' -d '{}')
  fi

  local code
  code=$(printf '%s' "$body" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null || echo "PARSE-ERR")

  if [ "$code" = "0" ]; then
    OK=$((OK + 1))
    printf '[ OK ] %-4s %s\n' "$method" "$path"
  else
    BAD=$((BAD + 1))
    FAILED="$FAILED\n  $method $path  code=$code"
    printf '[FAIL] %-4s %s  code=%s  %s\n' "$method" "$path" "$code" "$(printf '%s' "$body" | head -c 200)"
  fi
}

echo "===== 成本分析（V1 保留端点 /api/admin/ai） ====="
hit GET "/api/admin/ai/cost/overview?days=7"
hit GET "/api/admin/ai/cost/trend?days=7"
hit GET "/api/admin/ai/cost/models?days=7"
hit GET "/api/admin/ai/cost/scenes?days=7"
hit GET "/api/admin/ai/cost/top-models?days=7&limit=5"
hit GET "/api/admin/ai/cost/top-users?days=7&limit=5"
hit GET "/api/admin/ai/cost/recent-requests?limit=5"
hit GET "/api/admin/ai/cost/alerts?limit=5"
hit GET "/api/admin/ai/usage/page?page=1&size=5"

echo
echo "===== 成本分析（V2 治理端点 /api/admin/cost） ====="
hit GET "/api/admin/cost/overview"
hit GET "/api/admin/cost/trend"
hit GET "/api/admin/cost/breakdown"
hit GET "/api/admin/cost/models"
hit GET "/api/admin/cost/scenes"
hit GET "/api/admin/cost/users"
hit GET "/api/admin/cost/optimization"
hit GET "/api/admin/cost/forecast"
hit GET "/api/admin/cost/daily-summary"
hit GET "/api/admin/cost/infrastructure"

echo
echo "===== Token 账单 / 预算 ====="
hit GET "/api/admin/token-usage/page?page=1&size=5"
hit GET "/api/admin/token-usage/statistics"
hit GET "/api/admin/budget/page?page=1&size=5"
hit GET "/api/admin/budget/status"
hit GET "/api/admin/budget/transactions?page=1&size=5"

echo
echo "===== 预警 / 保护 / 优化 / Agent ====="
hit GET "/api/admin/cost-alert/page?page=1&size=5"
hit GET "/api/admin/cost-protection/rules"
hit GET "/api/admin/cost-protection/status"
hit GET "/api/admin/cost-protection/logs?page=1&size=5"
hit GET "/api/admin/cost-optimization/strategies"
hit GET "/api/admin/cost-optimization/history?page=1&size=5"
hit GET "/api/admin/agent/traces?page=1&size=5"

echo
echo "===== 可安全执行的操作端点 ====="
hit POST "/api/admin/cost-alert/evaluate"
hit POST "/api/admin/cost-alert/anomaly-scan"
hit POST "/api/admin/cost/daily-summary/rebuild"

echo
echo "===== 关键状态抽查 ====="
curl -s --noproxy '*' "$BASE/api/admin/cost-protection/status" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print("保护等级:",d["level"],"(","levelCode=",d["levelCode"],")","紧急开关:",d["emergencyStopped"],"未恢复CRITICAL:",d["openCriticalAlerts"],"最高预算使用率:",d["maxBudgetUsageRate"],")")'

echo
echo "======================================"
echo "通过 $OK 个，失败 $BAD 个"
if [ "$BAD" -gt 0 ]; then
  echo -e "失败清单：$FAILED"
  exit 1
fi
echo "全部管理接口 code=0"
