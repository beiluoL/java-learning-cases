-- ============================================================
-- 演示库初始化脚本（H2 内存库，MODE=MySQL）
-- 由 MainApplication 在启动时读取并执行（Resources.getResourceAsString）
-- ============================================================

DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_name   VARCHAR(50)  NOT NULL,
    age         INT,
    email       VARCHAR(100),
    create_time TIMESTAMP
);

CREATE TABLE t_order (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    order_no    VARCHAR(50),
    amount      DECIMAL(10, 2),
    status      VARCHAR(20)
);

-- 种子数据
INSERT INTO t_user (user_name, age, email, create_time) VALUES
('张三', 18, 'zhangsan@example.com', NOW()),
('李四', 25, 'lisi@example.com',     NOW()),
('王五', 30, 'wangwu@example.com',   NOW()),
('赵六', 22, 'zhaoliu@example.com',  NOW());

INSERT INTO t_order (user_id, order_no, amount, status) VALUES
(1, 'NO2024001', 99.50,  'PAID'),
(1, 'NO2024002', 199.00, 'PAID'),
(2, 'NO2024003', 59.90,  'PENDING'),
(3, 'NO2024004', 299.99, 'PAID');
