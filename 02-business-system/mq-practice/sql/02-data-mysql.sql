-- =====================================================================================
--  初始化业务数据（MySQL 8.0）
--  mysql -uroot -p mq_practice < 02-data-mysql.sql
-- =====================================================================================

USE `mq_practice`;

-- 商品：product_no 固定，便于前端与文档里直接引用
INSERT INTO `mq_product` (`id`, `product_no`, `name`, `price`, `status`, `cover`, `description`, `version`)
VALUES (1, 'P1001', 'iPhone 16 Pro 256G', 7999.00, 1, '', '秒杀主推款，库存仅 20 件，用于演示防超卖', 0),
       (2, 'P1002', '小米 15 Ultra 512G', 6499.00, 1, '', '安卓影像旗舰', 0),
       (3, 'P1003', '华为 Mate 70 Pro 256G', 6999.00, 1, '', '鸿蒙旗舰', 0),
       (4, 'P1004', 'iPad Air 11 英寸 M3', 4799.00, 1, '', '轻办公平板', 0),
       (5, 'P1005', 'MacBook Air 13 M4', 8999.00, 0, '', '当前为下架状态，可在控制台一键上架触发事件', 0),
       (6, 'P1006', 'AirPods Pro 3', 1899.00, 1, '', '降噪耳机', 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 库存：商品 1 故意给 20 件，方便用 100 并发压测验证「不超卖」
INSERT INTO `mq_stock` (`product_id`, `total`, `available`, `version`)
VALUES (1, 20, 20, 0),
       (2, 200, 200, 0),
       (3, 150, 150, 0),
       (4, 80, 80, 0),
       (5, 50, 50, 0),
       (6, 300, 300, 0)
ON DUPLICATE KEY UPDATE `total` = VALUES(`total`), `available` = VALUES(`available`);

-- 搜索索引初始快照（模拟 ES 已建索引）
INSERT INTO `mq_search_index` (`product_id`, `product_no`, `name`, `price`, `status`, `doc_version`)
VALUES (1, 'P1001', 'iPhone 16 Pro 256G', 7999.00, 1, 0),
       (2, 'P1002', '小米 15 Ultra 512G', 6499.00, 1, 0),
       (3, 'P1003', '华为 Mate 70 Pro 256G', 6999.00, 1, 0),
       (4, 'P1004', 'iPad Air 11 英寸 M3', 4799.00, 1, 0),
       (5, 'P1005', 'MacBook Air 13 M4', 8999.00, 0, 0),
       (6, 'P1006', 'AirPods Pro 3', 1899.00, 1, 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `price` = VALUES(`price`);
