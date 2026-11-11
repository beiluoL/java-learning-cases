#!/bin/bash
# 告警恢复 + 紧急开关 + 恢复后放行 验证
set -u
BASE=http://127.0.0.1:8091
MYSQL="docker exec ai-cost-mysql mysql -uroot -proot -N -e"
SQL() { $MYSQL "$1" 2>&1 | grep -v Warning; }
CHAT() { curl -s --noproxy '*' -X POST "$BASE/api/ai/chat" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"demo-user\",\"message\":\"$1\",\"scene\":\"CHAT\"}"; }

echo "=== G) 恢复额度到 2.00 后自动恢复告警 ==="
SQL "UPDATE ai_cost.ai_budget_policy SET daily_limit=2.00 WHERE id=4;"
curl -s --noproxy '*' -X POST "$BASE/api/admin/cost-alert/evaluate" \
  | python3 -c "import sys,json;h=json.load(sys.stdin)['data']['thresholdHits'];print('恢复档位:',[x['threshold'] for x in h if x['recovered']])"
SQL "SELECT alert_type, threshold_value, status, resolved_at IS NOT NULL FROM ai_cost.ai_cost_alert WHERE alert_type='BUDGET_THRESHOLD' AND target_id='CHAT' ORDER BY threshold_value;"

echo
echo "=== H) 恢复后正常问答（应 code=0）==="
CHAT "预算恢复后这条请求必须成功" | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'),'cacheHit=',d['data'].get('cacheHit') if d.get('code')==0 else '-')"

echo
echo "=== I) 紧急停用（缺原因应被拒绝：code=40001）==="
curl -s --noproxy '*' -X POST "$BASE/api/admin/cost-protection/emergency-stop" \
  -H 'Content-Type: application/json' -d '{"operator":"admin","reason":""}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'),'message=',d.get('message'))"

echo
echo "=== J) 紧急停用（带原因，应成功并产生审计日志）==="
curl -s --noproxy '*' -X POST "$BASE/api/admin/cost-protection/emergency-stop" \
  -H 'Content-Type: application/json' -d '{"operator":"admin","reason":"演练：验证全局阻断开关"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'))"
curl -s --noproxy '*' "$BASE/api/admin/cost-protection/status" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('level=',d.get('level'),'emergency=',d.get('emergencyStopped'))"

echo
echo "=== K) 停用期间请求必须被阻断 ==="
CHAT "紧急停用期间这条请求必须被拒绝" | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'),'message=',d.get('message'))"

echo
echo "=== L) 恢复运行 ==="
curl -s --noproxy '*' -X POST "$BASE/api/admin/cost-protection/resume" \
  -H 'Content-Type: application/json' -d '{"operator":"admin","reason":"演练结束，恢复运行"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'))"
curl -s --noproxy '*' "$BASE/api/admin/cost-protection/status" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('level=',d.get('level'),'emergency=',d.get('emergencyStopped'))"

echo
echo "=== M) 恢复后请求放行 ==="
CHAT "恢复运行后这条请求必须成功" | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'),'cacheHit=',d['data'].get('cacheHit') if d.get('code')==0 else '-')"

echo
echo "=== N) 保护动作审计日志 ==="
SQL "SELECT action, from_level, to_level, operator, reason FROM ai_cost.ai_cost_protection_log ORDER BY id;"
