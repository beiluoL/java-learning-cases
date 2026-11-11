#!/usr/bin/env bash
#
# 告警恢复 + 紧急开关 + 恢复后放行 验证脚本
#
# 用途     ：验证额度恢复后告警自动 RESOLVED、紧急停用的准入校验与阻断效果、
#            恢复运行后请求重新放行、以及保护动作的审计留痕
# 用法     ：bash samples/verify-alert-recovery.sh
# 参数     ：无
# 环境变量 ：BASE（默认 http://127.0.0.1:8091）
#            MYSQL_PASSWORD（默认 root，与 infra/docker/ai-cost-compose.yml 一致）
# 执行前提 ：后端已启动；MySQL 容器 ai-cost-mysql 为 healthy；Redis 可用
# 副作用   ：1) 会把 ai_budget_policy(id=4).daily_limit 恢复为 2.00（本脚本的既定动作）
#            2) 会真实触发一次紧急停用与恢复，期间所有付费调用被拒绝
#            3) 会写入 ai_cost_protection_log 审计记录（保留用于审计）
# 失败处理 ：**关键**——若脚本在「紧急停用」之后中断，系统会停留在 BLOCKED 状态，
#            所有付费调用被拒。因此用 trap 在退出时（含异常/Ctrl+C）无条件执行
#            resume + 额度恢复，绝不让环境停在停用状态。
#
# 注意     ：不使用 set -e（探测型脚本需逐步收集证据），但开启 pipefail。
#
set -uo pipefail

BASE="${BASE:-http://127.0.0.1:8091}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL="docker exec ai-cost-mysql mysql -uroot -p${MYSQL_PASSWORD} -N -e"
POLICY_ID=4
RESTORE_LIMIT="2.00"

SQL() { $MYSQL "$1" 2>&1 | grep -v "Using a password" || true; }

CHAT() {
  curl -s --noproxy '*' -X POST "$BASE/api/ai/chat" -H 'Content-Type: application/json' \
    -d "{\"message\":\"$1\",\"useRag\":false}"
}

# 退出兜底：无论正常结束还是被中断，都保证不留在停用状态
cleanup() {
  SQL "UPDATE ai_cost.ai_budget_policy SET daily_limit=${RESTORE_LIMIT} WHERE id=${POLICY_ID};" >/dev/null 2>&1
  curl -s --noproxy '*' -X POST "$BASE/api/admin/cost-protection/resume" \
    -H 'Content-Type: application/json' \
    -d '{"operator":"system","reason":"演练脚本退出兜底：确保不停留在停用状态"}' >/dev/null 2>&1 || true
  echo "[INFO] 退出兜底完成：额度已恢复为 ${RESTORE_LIMIT}，保护状态已 resume"
}
trap cleanup EXIT

echo "=== G) 恢复额度到 ${RESTORE_LIMIT} 后自动恢复告警 ==="
SQL "UPDATE ai_cost.ai_budget_policy SET daily_limit=${RESTORE_LIMIT} WHERE id=${POLICY_ID};"
curl -s --noproxy '*' -X POST "$BASE/api/admin/cost-alert/evaluate" \
  | python3 -c "import sys,json;h=json.load(sys.stdin)['data']['thresholdHits'];print('恢复档位:',[x['threshold'] for x in h if x['recovered']])"
SQL "SELECT alert_type, threshold_value, status, resolved_at IS NOT NULL FROM ai_cost.ai_cost_alert
     WHERE alert_type='BUDGET_THRESHOLD' AND target_id='CHAT' ORDER BY threshold_value;"

echo
echo "=== H) 恢复后正常问答（应 code=0）==="
CHAT "预算恢复后这条请求必须成功" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'),'cacheHit=',d['data'].get('cacheHit') if d.get('code')==0 else '-')"

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
CHAT "紧急停用期间这条请求必须被拒绝" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'),'message=',d.get('message'))"

echo
echo "=== L) 恢复运行 ==="
curl -s --noproxy '*' -X POST "$BASE/api/admin/cost-protection/resume" \
  -H 'Content-Type: application/json' -d '{"operator":"admin","reason":"演练结束，恢复运行"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'))"
curl -s --noproxy '*' "$BASE/api/admin/cost-protection/status" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('level=',d.get('level'),'emergency=',d.get('emergencyStopped'))"

echo
echo "=== M) 恢复后请求放行 ==="
CHAT "恢复运行后这条请求必须成功" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'),'cacheHit=',d['data'].get('cacheHit') if d.get('code')==0 else '-')"

echo
echo "=== N) 保护动作审计日志 ==="
SQL "SELECT action, from_level, to_level, operator, reason FROM ai_cost.ai_cost_protection_log ORDER BY id;"
