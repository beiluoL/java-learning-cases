-- V5 · 成本治理平台的初始策略数据
-- 注意：这里所有金额/价格均为 DEMO 口径，不代表任何供应商或云厂商真实报价。

-- ============================================================
-- 1. 自动保护规则（按预算使用率分级，阈值可由管理员修改）
--    等级语义：1 正常 / 2 警告 / 3 限制 / 4 严重保护 / 5 预算阻断
-- ============================================================
INSERT INTO ai_cost_protection_rule
    (rule_code, rule_name, protection_level, condition_type, threshold_value, action, priority, enabled, remark)
VALUES ('budget_usage_50', '预算使用率达 50%', 2, 'BUDGET_USAGE_RATE', 0.5000, 'NOTIFY', 50, 1, '提醒，不改变调用链'),
       ('budget_usage_70', '预算使用率达 70%', 2, 'BUDGET_USAGE_RATE', 0.7000, 'NOTIFY', 40, 1, '通知管理员'),
       ('budget_usage_85', '预算使用率达 85%', 3, 'BUDGET_USAGE_RATE', 0.8500, 'DOWNGRADE_MODEL', 30, 1, '优先低成本模型、限制非核心任务'),
       ('budget_usage_95', '预算使用率达 95%', 4, 'BUDGET_USAGE_RATE', 0.9500, 'DISABLE_AGENT', 20, 1, '暂停高成本 Agent，关闭非必要 Query Rewrite'),
       ('budget_usage_100', '预算使用率达 100%', 5, 'BUDGET_USAGE_RATE', 1.0000, 'BLOCK_REQUEST', 10, 1, '阻断新的付费调用')
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name);

-- ============================================================
-- 2. 场景级预算 / Agent 预算（DEMO 额度）
-- ============================================================
INSERT INTO ai_budget_policy
    (budget_type, target_id, daily_limit, monthly_limit, per_request_limit,
     max_input_tokens, max_output_tokens, allow_overrun, enabled, currency,
     max_requests, max_concurrency, max_agent_steps, max_tool_calls, alert_threshold, overrun_policy, remark)
VALUES ('SCENE', 'CHAT', 2.00000000, 40.00000000, 0.05000000, 4000, 1200, 0, 1, 'USD', 2000, 8, NULL, NULL, 0.8000, 'DOWNGRADE', 'DEMO 场景预算：普通问答'),
       ('SCENE', 'RAG', 3.00000000, 60.00000000, 0.08000000, 8000, 1500, 0, 1, 'USD', 2000, 8, NULL, NULL, 0.8000, 'DOWNGRADE', 'DEMO 场景预算：RAG 问答'),
       ('AGENT', '__ALL__', 1.00000000, 20.00000000, 0.20000000, 8000, 2000, 0, 1, 'USD', 500, 4, 8, 16, 0.8000, 'BLOCK', 'DEMO Agent 预算：步数与工具调用上限'),
       ('KNOWLEDGE_BASE', 'default', 3.00000000, 60.00000000, NULL, NULL, NULL, 0, 1, 'USD', NULL, NULL, NULL, NULL, 0.8000, 'DOWNGRADE', 'DEMO 知识库预算')
ON DUPLICATE KEY UPDATE daily_limit = VALUES(daily_limit);

-- ============================================================
-- 3. 基础设施成本（DEMO：用于演示分摊口径，非真实云账单）
-- ============================================================
INSERT INTO ai_infrastructure_cost
    (resource_type, resource_name, provider, period_start, period_end, amount, currency,
     allocation_method, allocated_amount, request_count, demo_flag, remark)
VALUES ('MYSQL', 'ai-cost-mysql(8.0)', 'self-hosted-docker', '2026-11-01', '2026-11-30', 18.00000000, 'USD',
        'ALLOCATED', 0.00000000, NULL, 1, 'DEMO 月成本，按请求量分摊'),
       ('REDIS', 'local-redis(8.8)', 'self-hosted', '2026-11-01', '2026-11-30', 6.00000000, 'USD',
        'ALLOCATED', 0.00000000, NULL, 1, 'DEMO 月成本，按预算操作次数分摊'),
       ('MILVUS', 'milvus-standalone(2.5.27)', 'self-hosted-docker', '2026-11-01', '2026-11-30', 24.00000000, 'USD',
        'ALLOCATED', 0.00000000, NULL, 1, 'DEMO 月成本，RAG 启用时才分摊');

-- ============================================================
-- 4. 回填每日汇总（基于真实账单表计算，不是造数）
-- ============================================================
INSERT INTO ai_cost_daily_summary
(summary_date, dimension, dimension_key, request_count, success_count, failed_count,
 input_tokens, output_tokens, cached_tokens, total_tokens, total_cost, cache_saved_cost)
SELECT DATE(created_at), 'ALL', '__ALL__',
       COUNT(*),
       SUM(request_status = 'SUCCESS'),
       SUM(request_status <> 'SUCCESS'),
       COALESCE(SUM(input_tokens), 0),
       COALESCE(SUM(output_tokens), 0),
       COALESCE(SUM(cached_input_tokens), 0),
       COALESCE(SUM(total_tokens), 0),
       COALESCE(SUM(total_cost), 0),
       COALESCE(SUM(cache_saved_cost), 0)
FROM ai_token_usage
GROUP BY DATE(created_at)
ON DUPLICATE KEY UPDATE request_count = VALUES(request_count),
                        success_count = VALUES(success_count),
                        failed_count  = VALUES(failed_count),
                        input_tokens  = VALUES(input_tokens),
                        output_tokens = VALUES(output_tokens),
                        total_tokens  = VALUES(total_tokens),
                        total_cost    = VALUES(total_cost);
