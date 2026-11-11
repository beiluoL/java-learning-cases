#!/usr/bin/env bash
#
# 预警分级验证脚本
#
# 用途     ：用真实 Redis 占用 + 临时收紧 SCENE/CHAT 额度，逐档触发 50/70/85/95/100%，
#            并验证告警去重、保护等级联动、超限请求被拒
# 用法     ：bash samples/verify-alert-levels.sh
# 参数     ：无
# 环境变量 ：BASE（默认 http://127.0.0.1:8091）
#            MYSQL_PASSWORD（默认 root，与 infra/docker/ai-cost-compose.yml 一致）
# 执行前提 ：后端已启动；MySQL 容器 ai-cost-mysql 为 healthy；本机 Redis 可用；
#            ai_budget_policy 中存在 id=4 的 SCENE/CHAT 策略
# 副作用   ：会临时把 ai_budget_policy(id=4).daily_limit 改小以制造越线，
#            **退出时（含异常退出）自动恢复为脚本开始前的原值**；
#            会写入 ai_cost_alert 告警记录（属正常业务数据，保留用于审计）
# 失败处理 ：任一步骤无响应会打印原始输出；不中途退出，保证跑完所有档位；
#            异常中断由 trap 恢复额度，避免留下「额度被改小」的脏状态
#
# 注意     ：本脚本不使用 set -e —— 探测型脚本需要「单步失败仍继续收集证据」，
#            因此每步都用显式判断而非依赖中止。pipefail 仍开启以暴露管道错误。
#
set -uo pipefail

BASE="${BASE:-http://127.0.0.1:8091}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL="docker exec ai-cost-mysql mysql -uroot -p${MYSQL_PASSWORD} -N -e"
POLICY_ID=4

# SQL：屏蔽 mysql 客户端输出的密码告警行
SQL() { $MYSQL "$1" 2>&1 | grep -v "Using a password" || true; }

EVAL() { curl -s --noproxy '*' -X POST "$BASE/api/admin/cost-alert/evaluate"; echo; }

OPEN() {
  SQL "SELECT threshold_value, alert_level, status FROM ai_cost.ai_cost_alert
       WHERE alert_type='BUDGET_THRESHOLD' AND target_id='CHAT' AND status<>'RESOLVED'
       ORDER BY threshold_value;"
}

SETLIMIT() { SQL "UPDATE ai_cost.ai_budget_policy SET daily_limit=$1 WHERE id=${POLICY_ID};"; }

# 记录原始额度，退出时恢复（含 Ctrl+C / 异常中断）
ORIGINAL_LIMIT="$(SQL "SELECT daily_limit FROM ai_cost.ai_budget_policy WHERE id=${POLICY_ID};" | tr -d '[:space:]')"
restore_limit() {
  if [ -n "${ORIGINAL_LIMIT:-}" ]; then
    SETLIMIT "$ORIGINAL_LIMIT" >/dev/null 2>&1
    echo "[INFO] 已恢复 ${POLICY_ID} 号策略 daily_limit=${ORIGINAL_LIMIT}"
  fi
}
trap 'restore_limit; echo "[INFO] 脚本结束"' EXIT

echo "=== 基线：CHAT 场景已占用（Redis 实时值）==="
redis-cli get "ai:{budget}:used:SCENE:CHAT:$(date -u +%Y-%m-%d)" || true
echo "=== 基线：原 daily_limit=${ORIGINAL_LIMIT:-<未读到>} ==="

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
echo "=== D) 去重：再评估两次，行数与 occurrence_count 合计不应增长 ==="
EVAL >/dev/null
EVAL >/dev/null
SQL "SELECT COUNT(*), SUM(occurrence_count) FROM ai_cost.ai_cost_alert
     WHERE alert_type='BUDGET_THRESHOLD' AND target_id='CHAT';"

echo
echo "=== E) 保护等级（应为 5 BLOCKED）==="
curl -s --noproxy '*' "$BASE/api/admin/cost-protection/status" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('level=',d.get('level'),'maxBudgetUsageRate=',d.get('maxBudgetUsageRate'))"

echo
echo "=== F) 超限后发起真实问答（应被拒绝，code=40302）==="
curl -s --noproxy '*' -X POST "$BASE/api/ai/chat" -H 'Content-Type: application/json' \
  -d '{"message":"预算已超限时这条请求必须被拒绝","useRag":false}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('code=',d.get('code'),'message=',d.get('message'))"
