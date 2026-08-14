-- ============================================================================
-- 初始化数据：给演示用的 5 个用户 + 3 笔订单
-- ============================================================================

INSERT INTO t_user (user_name, email, age, status)
VALUES ('张三', 'zhangsan@example.com', 25, 1);
INSERT INTO t_user (user_name, email, age, status)
VALUES ('李四', 'lisi@example.com', 30, 1);
INSERT INTO t_user (user_name, email, age, status)
VALUES ('王五', 'wangwu@example.com', 28, 1);
INSERT INTO t_user (user_name, email, age, status)
VALUES ('赵六', 'zhaoliu@example.com', 35, 0);
INSERT INTO t_user (user_name, email, age, status)
VALUES ('钱七', 'qianqi@example.com', 22, 1);

INSERT INTO t_order (order_no, user_id, amount)
VALUES ('ORD20260814001', 1, 199.00);
INSERT INTO t_order (order_no, user_id, amount)
VALUES ('ORD20260814002', 1, 88.50);
INSERT INTO t_order (order_no, user_id, amount)
VALUES ('ORD20260814003', 2, 1299.00);
