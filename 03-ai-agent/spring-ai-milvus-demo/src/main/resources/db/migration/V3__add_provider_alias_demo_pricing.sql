-- 供应商可能返回与请求不同的「实际模型名」（实测 DeepSeek 返回 deepseek-flash）。
-- 账单必须保存供应商返回的真实模型名，因此价格表需要为该别名提供可计费版本，
-- 否则 CostCalculator 会正确地标 UNPRICED 并写告警（这是防止用错价格的安全行为，不是故障）。
-- 注意：以下单价仍是 DEMO 示例价，不是供应商当前真实报价。
INSERT INTO ai_model_pricing
(provider, model_name, model_type, input_price, output_price, cached_input_price, currency, version, effective_from, effective_to, enabled, demo_flag)
VALUES
('openai', 'deepseek-flash', 'CHAT', 0.27000000, 1.10000000, 0.02700000, 'USD', 'DEMO-v1', '2020-01-01 00:00:00.000', NULL, 1, 1);
