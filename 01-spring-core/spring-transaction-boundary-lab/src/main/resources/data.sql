-- ============================================================================
-- Q021-EXT 事务边界实验室 初始化数据
-- 三个订单分别对应三个独立场景，互不干扰：
--   订单 1 → 核心场景（内层 REQUIRED 抛异常，被外层 catch）
--   订单 2 → 对照场景 Test04（内层自己 catch 异常，不传播到事务代理）
--   订单 3 → 对照场景 Test05（内层 REQUIRES_NEW，独立物理事务回滚）
-- 使用 MERGE 保证脚本可重复执行（每次启动/每次测试重置都不会主键冲突）
-- ============================================================================

MERGE INTO orders (id, status, created_at) KEY (id) VALUES (1, 'PENDING', CURRENT_TIMESTAMP);
MERGE INTO orders (id, status, created_at) KEY (id) VALUES (2, 'PENDING', CURRENT_TIMESTAMP);
MERGE INTO orders (id, status, created_at) KEY (id) VALUES (3, 'PENDING', CURRENT_TIMESTAMP);
