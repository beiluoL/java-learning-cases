#!/bin/bash
# 预警分级验证：用真实 Redis 占用 + 临时收紧 SCENE/CHAT 额度，逐档触发 50/70/85/95/100
set -u
BASE=http://127.0.0.1:8091
MYSQL="docker exec ai-cost-mysql mysql -uroot -proot -N -e"
SQL() { $MYSQL "$1" 2>&1 | grep -v Warning; }
EVAL() { curl -s --noproxy '*' -X POST "$BASE/api/admin/cost-alert/evaluate"; echo; }
OPEN() { SQL "SELECT threshold_value, alert_level, status FROM ai_cost.ai_cost_alert WHERE alert_type='BUDGET_THRESHOLD' AND target_id='CHAT' AND status<>'RESOLVED' ORDER BY threshold_value;"; }
SETLIMIT() { SQL "UPDATE ai_cost.ai_budget_policy SET daily_limit=$1 WHERE id=4;"; }

echo "=== 基线：CHAT 场景已占用 ==="
redis-cli get ai:{budget}:used:SCENE:CHAT:$(date -u +%Y-%m-%d)

echo
echo "=== A) 额度 0.00000800 → 使用率 80%，应触发 50% + 70% ==="
SETLIMIT 0.00000800
EVAL
OPEN

echo
echo "=== B) 额度 0.00000660 → 使用率 96.97%，应新增 85% + 95% ==="
SETLIMIT 0.00000660
EVAL
OPEN

echo
echo "=== C) 额度 0.00000600 → 使用率 106.67%，应新增 100% ==="
SETLIMIT 0.00000600
EVAL
OPEN

echo
echo "=== D) 去重：再执行两次，行数与 occurrence_count 不应增长 ==="
EVAL >/dev/null
EVAL >/dev/null
SQL "SELECT COUNT(*), SUM(occurrence_count) FROM ai_cost.ai_cost_alert WHERE alert_type='BUDGET_THRESHOLD' AND target_id='CHAT';"

echo
echo "=== E) 保护等级（应为 5 BLOCKED）==="
curl -s --noproxy '*' "$BASE/api/admin/cost-protection/status" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('level=',d.get('level'),'maxBudgetUsageRate=',d.get('maxBudgetUsageRate'))"

echo
echo "=== F) 超限后发起真实问答（应被拒绝，code=40302）==="
curl -s --noproxy '*' -X POST "$BASE/api/ai/chat" -H 'Content-Type: application/json' \
  -d '{"userId":"demo-user","message":"预算已超限时这条请求必须被拒绝","scene":"CHAT"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'),'message=',d.get('message'))"
