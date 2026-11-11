-- 初始化示例数据与默认策略。
-- 重要：以下价格均为 DEMO 示例价（demo_flag=1），不是任何供应商的当前真实报价，
-- 禁止据此做真实财务结算；上线前必须由运维按合同价替换并置 demo_flag=0。

INSERT INTO ai_model_pricing
(provider, model_name, model_type, input_price, output_price, cached_input_price, currency, version, effective_from, effective_to, enabled, demo_flag)
VALUES
('deepseek', 'deepseek-chat',      'CHAT', 0.27000000, 1.10000000, 0.02700000, 'USD', 'DEMO-v1', '2020-01-01 00:00:00.000', NULL, 1, 1),
('deepseek', 'deepseek-reasoner',  'CHAT', 0.55000000, 2.19000000, 0.05500000, 'USD', 'DEMO-v1', '2020-01-01 00:00:00.000', NULL, 1, 1),
('openai',   'gpt-4o-mini',         'CHAT', 0.15000000, 0.60000000, 0.07500000, 'USD', 'DEMO-v1', '2020-01-01 00:00:00.000', NULL, 1, 1),
('mock',     'mock-chat-model',     'CHAT', 0.10000000, 0.20000000, 0.01000000, 'USD', 'DEMO-v1', '2020-01-01 00:00:00.000', NULL, 1, 1),
('local',    'local-chat-model',    'CHAT', 0.00000000, 0.00000000, 0.00000000, 'USD', 'DEMO-v1', '2020-01-01 00:00:00.000', NULL, 1, 1),
('local',    'local-hashing-embedding', 'EMBEDDING', 0.00000000, 0.00000000, 0.00000000, 'USD', 'DEMO-v1', '2020-01-01 00:00:00.000', NULL, 1, 1);

-- 默认预算策略：系统全局 + Demo 用户的日/月预算。
-- 开发环境固定 Demo 用户见 DemoUserIdentityProvider；生产必须替换为真实认证。
INSERT INTO ai_budget_policy
(budget_type, target_id, daily_limit, monthly_limit, per_request_limit, max_input_tokens, max_output_tokens, allow_overrun, enabled, currency, remark)
VALUES
('GLOBAL',  '__ALL__',   50.00000000, 1000.00000000, 1.00000000, 32000, 8000, 0, 1, 'USD', 'DEMO：系统全局预算'),
('DAILY',   'demo-user',  5.00000000,          NULL, 0.10000000, 16000, 4000, 0, 1, 'USD', 'DEMO：单用户每日预算'),
('MONTHLY', 'demo-user',        NULL, 100.00000000, 0.10000000, 16000, 4000, 0, 1, 'USD', 'DEMO：单用户每月预算');
