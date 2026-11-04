-- 演示种子数据。每次启动 h2 profile 都会重新灌入。
INSERT INTO product (id, name, price, description, stock) VALUES
    (1, '客制化机械键盘', 399.00, '热销 75% 配列，茶轴客制化', 50),
    (2, '27寸 4K 显示器', 1599.00, 'IPS 广色域，Type-C 一线连', 30),
    (3, '人体工学办公椅', 899.00, '护腰护颈，久坐不累', 20);

INSERT INTO app_user (id, username, password, nickname) VALUES
    (1, 'alice', '123456', 'Alice'),
    (2, 'bob', '123456', 'Bob');

-- 分布式锁场景：商品 1 的初始库存 100，用于并发扣减演示（最终应扣到 0 无超卖）
INSERT INTO stock (id, product_id, count) VALUES (1, 1, 100);
