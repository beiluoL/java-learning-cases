-- =====================================================================================
--  Spring Boot + Vue3 全栈 MQ 实战 —— MySQL 8.0 生产版建表脚本
--  说明：
--    1) 本脚本为「生产环境（mysql profile）」权威版本，含完整注释、索引、字符集。
--    2) 后端默认 h2 profile 会在启动时自动执行 resources/db/schema.sql（与本文列结构一致，
--       但去掉了 H2 不兼容的 ENGINE / COMMENT / ON UPDATE 语法）。
--    3) 执行顺序：先本文件，再 02-data-mysql.sql。
--  命令：mysql -uroot -p < 01-schema-mysql.sql
-- =====================================================================================

CREATE DATABASE IF NOT EXISTS `mq_practice`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `mq_practice`;

-- -------------------------------------------------------------------------------------
-- 1. 商品表
-- -------------------------------------------------------------------------------------
DROP TABLE IF EXISTS `mq_product`;
CREATE TABLE `mq_product`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_no`  VARCHAR(32)  NOT NULL COMMENT '商品编码（业务唯一键）',
    `name`        VARCHAR(128) NOT NULL COMMENT '商品名称',
    `price`       DECIMAL(12, 2) NOT NULL COMMENT '售价（元）',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-下架 1-上架',
    `cover`       VARCHAR(255)          DEFAULT NULL COMMENT '封面图 URL',
    `description` VARCHAR(512)          DEFAULT NULL COMMENT '描述',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '数据版本号（每次变更 +1，用于 MQ 消息乱序识别）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_no` (`product_no`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品表（场景三：商品变更事件源）';

-- -------------------------------------------------------------------------------------
-- 2. 库存表
--    扣减一律使用「条件更新」：UPDATE ... SET available = available - ? WHERE available >= ?
--    依赖 InnoDB 行锁保证原子性，天然防超卖；affectedRows = 0 即库存不足。
-- -------------------------------------------------------------------------------------
DROP TABLE IF EXISTS `mq_stock`;
CREATE TABLE `mq_stock`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`  BIGINT   NOT NULL COMMENT '商品 ID',
    `total`       INT      NOT NULL COMMENT '总库存',
    `available`   INT      NOT NULL COMMENT '可用库存',
    `version`     INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（预留）',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_id` (`product_id`),
    KEY `idx_available` (`available`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='库存表';

-- -------------------------------------------------------------------------------------
-- 3. 订单表
--    status 生命周期：
--      QUEUING  -> 已受理，消息已投递 MQ，等待异步落单（前端"排队中"）
--      UNPAID   -> 订单已创建，未支付（此时已扣库存，并已投递 30 分钟延迟消息）
--      PAID     -> 已支付
--      CANCELLED-> 超时/主动取消（库存已回补）
--      FAILED   -> 异步落单失败（库存不足等）
-- -------------------------------------------------------------------------------------
DROP TABLE IF EXISTS `mq_order`;
CREATE TABLE `mq_order`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`       VARCHAR(40)  NOT NULL COMMENT '订单号（全局唯一，MQ 幂等业务键）',
    `user_id`        BIGINT       NOT NULL COMMENT '用户 ID',
    `product_id`     BIGINT       NOT NULL COMMENT '商品 ID',
    `product_name`   VARCHAR(128)          DEFAULT NULL COMMENT '商品名称快照',
    `quantity`       INT          NOT NULL DEFAULT 1 COMMENT '购买数量',
    `amount`         DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '订单金额',
    `status`         VARCHAR(20)  NOT NULL COMMENT '订单状态（见上方生命周期说明）',
    `expire_time`    DATETIME              DEFAULT NULL COMMENT '支付截止时间（创建时间 + 延迟时长）',
    `pay_time`       DATETIME              DEFAULT NULL COMMENT '支付时间',
    `cancel_time`    DATETIME              DEFAULT NULL COMMENT '取消时间',
    `cancel_reason`  VARCHAR(128)          DEFAULT NULL COMMENT '取消原因',
    `delay_seconds`  INT          NOT NULL DEFAULT 1800 COMMENT '本单延迟时长（秒），演示时可传 10',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status_expire` (`status`, `expire_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单表（场景一/场景二）';

-- -------------------------------------------------------------------------------------
-- 4. 幂等日志表（消费端第二道防线）
--    设计要点：
--      * biz_key 建立唯一索引 —— 数据库层面强约束，即使 Redis 被 flush / 多实例并发也只会成功一条；
--      * 与 Redis SETNX 组成「双层幂等」：Redis 挡 99% 重复（快），DB 唯一键兜底（准）。
-- -------------------------------------------------------------------------------------
DROP TABLE IF EXISTS `mq_idempotent_log`;
CREATE TABLE `mq_idempotent_log`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `biz_key`     VARCHAR(160) NOT NULL COMMENT '业务唯一键（如 order:create:NO202611040001）',
    `scene`       VARCHAR(64)  NOT NULL COMMENT '消费场景（ORDER_CREATE / ORDER_CANCEL / PRODUCT_CACHE ...）',
    `message_id`  VARCHAR(64)           DEFAULT NULL COMMENT 'MQ 消息 ID',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '处理状态：1-处理中 2-成功 3-失败',
    `payload`     TEXT COMMENT '消息体快照（排查用）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_key` (`biz_key`),
    KEY `idx_scene_create` (`scene`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='消费幂等日志表';

-- -------------------------------------------------------------------------------------
-- 5. 消息发送日志表（生产端「本地消息表」雏形）
--    配合 Publisher Confirm / Return 回调回填投递结果，实现"消息不丢"的可观测闭环：
--      SENDING   -> 已入库、待确认
--      CONFIRMED -> broker 已落盘（confirm ack=true）
--      RETURNED  -> 到达交换机但路由失败（mandatory + Return 回调）
--      FAILED    -> 发送异常 / nack
-- -------------------------------------------------------------------------------------
DROP TABLE IF EXISTS `mq_message_log`;
CREATE TABLE `mq_message_log`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `message_id`   VARCHAR(64) NOT NULL COMMENT '消息唯一 ID（UUID）',
    `exchange_name` VARCHAR(64)          DEFAULT NULL COMMENT '交换机',
    `routing_key`  VARCHAR(64)          DEFAULT NULL COMMENT '路由键',
    `scene`        VARCHAR(64)          DEFAULT NULL COMMENT '业务场景',
    `biz_key`      VARCHAR(160)         DEFAULT NULL COMMENT '业务键',
    `payload`      TEXT COMMENT '消息体 JSON',
    `status`       VARCHAR(20) NOT NULL DEFAULT 'SENDING' COMMENT '投递状态',
    `retry_count`  INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    `error_msg`    VARCHAR(512)         DEFAULT NULL COMMENT '失败原因',
    `cost_ms`      BIGINT               DEFAULT NULL COMMENT '发送耗时（毫秒）',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_scene_create` (`scene`, `create_time`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='消息发送日志表';

-- -------------------------------------------------------------------------------------
-- 6. 搜索索引表（模拟 Elasticsearch）
--    真实项目中这里应写 ES；本项目用一张表 + 独立消费者模拟，
--    目的是演示「同一个商品事件 -> 两个队列 -> 两个消费者并行、互不阻塞」。
-- -------------------------------------------------------------------------------------
DROP TABLE IF EXISTS `mq_search_index`;
CREATE TABLE `mq_search_index`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`  BIGINT       NOT NULL COMMENT '商品 ID',
    `product_no`  VARCHAR(32)  NOT NULL COMMENT '商品编码',
    `name`        VARCHAR(128) NOT NULL COMMENT '名称（检索字段）',
    `price`       DECIMAL(12, 2) NOT NULL COMMENT '价格',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '上架状态',
    `doc_version` INT          NOT NULL DEFAULT 0 COMMENT '文档版本（= 商品 version，用于丢弃乱序旧消息）',
    `indexed_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '索引更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_id` (`product_id`),
    KEY `idx_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='模拟 ES 的搜索索引表（场景三）';
