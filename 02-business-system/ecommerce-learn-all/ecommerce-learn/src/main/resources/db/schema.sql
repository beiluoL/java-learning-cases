-- =====================================================================
-- 电商与零售学习项目 - 数据库脚本（MySQL 8）
-- 字符集 utf8mb4，引擎 InnoDB
-- 说明：id 采用雪花算法（MyBatis-Plus ASSIGN_ID）生成，故不设置 AUTO_INCREMENT
-- =====================================================================

CREATE DATABASE IF NOT EXISTS ecommerce_learn
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE ecommerce_learn;

-- --------------------------- 用户 ---------------------------
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT       NOT NULL COMMENT '用户ID（雪花）',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密密码',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    email       VARCHAR(64)  DEFAULT NULL COMMENT '邮箱',
    avatar      VARCHAR(255) DEFAULT NULL COMMENT '头像',
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 USER/ADMIN',
    status      INT          NOT NULL DEFAULT 1 COMMENT '1 正常 0 禁用',
    created_at  DATETIME     DEFAULT NULL,
    updated_at  DATETIME     DEFAULT NULL,
    deleted     INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- --------------------------- 分类 ---------------------------
DROP TABLE IF EXISTS t_category;
CREATE TABLE t_category (
    id          BIGINT      NOT NULL,
    name        VARCHAR(64) NOT NULL COMMENT '分类名',
    parent_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '父级ID，0 为一级',
    level       INT         NOT NULL DEFAULT 1 COMMENT '层级',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序',
    icon        VARCHAR(255) DEFAULT NULL COMMENT '图标',
    created_at  DATETIME    DEFAULT NULL,
    updated_at  DATETIME    DEFAULT NULL,
    deleted     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类';

-- --------------------------- 品牌 ---------------------------
DROP TABLE IF EXISTS t_brand;
CREATE TABLE t_brand (
    id          BIGINT      NOT NULL,
    name        VARCHAR(64) NOT NULL COMMENT '品牌名',
    logo        VARCHAR(255) DEFAULT NULL COMMENT 'Logo',
    description VARCHAR(255) DEFAULT NULL COMMENT '描述',
    created_at  DATETIME    DEFAULT NULL,
    updated_at  DATETIME    DEFAULT NULL,
    deleted     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '品牌表';

-- --------------------------- SPU ---------------------------
DROP TABLE IF EXISTS t_spu;
CREATE TABLE t_spu (
    id           BIGINT       NOT NULL,
    title        VARCHAR(128) NOT NULL COMMENT '商品标题',
    category_id  BIGINT       NOT NULL COMMENT '分类ID',
    brand_id     BIGINT       DEFAULT NULL COMMENT '品牌ID',
    description  VARCHAR(512) DEFAULT NULL COMMENT '描述',
    main_image   VARCHAR(255) DEFAULT NULL COMMENT '主图',
    images       TEXT         DEFAULT NULL COMMENT '图集 JSON',
    status       INT          NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
    created_at   DATETIME     DEFAULT NULL,
    updated_at   DATETIME     DEFAULT NULL,
    deleted      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SPU 商品';

-- --------------------------- SKU ---------------------------
DROP TABLE IF EXISTS t_sku;
CREATE TABLE t_sku (
    id           BIGINT       NOT NULL,
    spu_id       BIGINT       NOT NULL COMMENT '所属SPU',
    sku_code     VARCHAR(64)  DEFAULT NULL COMMENT 'SKU编码',
    specs        VARCHAR(255) DEFAULT NULL COMMENT '规格 JSON',
    price        DECIMAL(10,2) NOT NULL COMMENT '价格',
    stock        INT          NOT NULL DEFAULT 0 COMMENT '实际库存',
    locked_stock INT          NOT NULL DEFAULT 0 COMMENT '锁定库存',
    image        VARCHAR(255) DEFAULT NULL COMMENT '图片',
    sales        INT          NOT NULL DEFAULT 0 COMMENT '销量',
    status       INT          NOT NULL DEFAULT 1 COMMENT '1 在售 0 下架',
    created_at   DATETIME     DEFAULT NULL,
    updated_at   DATETIME     DEFAULT NULL,
    deleted      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_spu (spu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SKU 库存单元';

-- --------------------------- 购物车 ---------------------------
DROP TABLE IF EXISTS t_cart_item;
CREATE TABLE t_cart_item (
    id          BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    sku_id      BIGINT      NOT NULL,
    spu_id      BIGINT      DEFAULT NULL,
    quantity    INT         NOT NULL DEFAULT 1,
    checked     INT         NOT NULL DEFAULT 1 COMMENT '1 选中 0 未选',
    created_at  DATETIME    DEFAULT NULL,
    updated_at  DATETIME    DEFAULT NULL,
    deleted     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购物车';

-- --------------------------- 订单 ---------------------------
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id                 BIGINT       NOT NULL,
    order_no           VARCHAR(32)  NOT NULL COMMENT '订单编号',
    user_id            BIGINT       NOT NULL,
    total_amount       DECIMAL(10,2) NOT NULL COMMENT '总金额',
    pay_amount         DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    status             INT          NOT NULL DEFAULT 0 COMMENT '见 OrderStatus',
    pay_type           VARCHAR(20)  DEFAULT NULL COMMENT 'alipay/wechat/seckill',
    trade_no           VARCHAR(64)  DEFAULT NULL COMMENT '第三方流水',
    receiver_name      VARCHAR(32)  DEFAULT NULL,
    receiver_phone     VARCHAR(20)  DEFAULT NULL,
    receiver_address   VARCHAR(255) DEFAULT NULL,
    remark             VARCHAR(255) DEFAULT NULL,
    pay_time           DATETIME     DEFAULT NULL,
    ship_time          DATETIME     DEFAULT NULL,
    finish_time        DATETIME     DEFAULT NULL,
    close_time         DATETIME     DEFAULT NULL,
    logistics_company  VARCHAR(64)  DEFAULT NULL,
    logistics_no       VARCHAR(64)  DEFAULT NULL,
    created_at         DATETIME     DEFAULT NULL,
    updated_at         DATETIME     DEFAULT NULL,
    deleted            INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单主表';

-- --------------------------- 订单明细 ---------------------------
DROP TABLE IF EXISTS t_order_item;
CREATE TABLE t_order_item (
    id          BIGINT       NOT NULL,
    order_id    BIGINT       NOT NULL,
    sku_id      BIGINT       DEFAULT NULL,
    spu_id      BIGINT       DEFAULT NULL,
    sku_name    VARCHAR(128) DEFAULT NULL COMMENT '标题快照',
    image       VARCHAR(255) DEFAULT NULL,
    specs       VARCHAR(255) DEFAULT NULL COMMENT '规格快照',
    price       DECIMAL(10,2) DEFAULT NULL,
    quantity    INT          NOT NULL DEFAULT 1,
    total_amount DECIMAL(10,2) DEFAULT NULL,
    created_at  DATETIME     DEFAULT NULL,
    updated_at  DATETIME     DEFAULT NULL,
    deleted     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细';

-- --------------------------- 秒杀活动 ---------------------------
DROP TABLE IF EXISTS t_seckill_activity;
CREATE TABLE t_seckill_activity (
    id          BIGINT       NOT NULL,
    spu_id      BIGINT       NOT NULL,
    sku_id      BIGINT       NOT NULL,
    title       VARCHAR(128) DEFAULT NULL,
    price       DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    stock       INT          NOT NULL DEFAULT 0 COMMENT '秒杀库存',
    sold        INT          NOT NULL DEFAULT 0 COMMENT '已售',
    start_time  DATETIME     DEFAULT NULL,
    end_time    DATETIME     DEFAULT NULL,
    status      INT          NOT NULL DEFAULT 1 COMMENT '0 未开始 1 进行中 2 已结束',
    created_at  DATETIME     DEFAULT NULL,
    updated_at  DATETIME     DEFAULT NULL,
    deleted     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '秒杀活动';

-- --------------------------- 秒杀订单记录 ---------------------------
DROP TABLE IF EXISTS t_seckill_order;
CREATE TABLE t_seckill_order (
    id          BIGINT      NOT NULL,
    activity_id BIGINT      NOT NULL,
    sku_id      BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    order_id    BIGINT      DEFAULT NULL,
    quantity    INT         NOT NULL DEFAULT 1,
    status      INT         NOT NULL DEFAULT 1 COMMENT '1 成功',
    created_at  DATETIME    DEFAULT NULL,
    updated_at  DATETIME    DEFAULT NULL,
    deleted     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_activity (activity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '秒杀订单记录';

-- =====================================================================
-- 初始化演示数据（用户请通过 /api/auth/register 注册，再用 SQL 提升为 ADMIN）
-- =====================================================================

INSERT INTO t_category (id, name, parent_id, level, sort) VALUES
    (1001, '手机数码', 0, 1, 1),
    (1002, '智能手机', 1001, 2, 1),
    (1003, '电脑办公', 0, 1, 2),
    (1004, '笔记本电脑', 1003, 2, 1);

INSERT INTO t_brand (id, name, description) VALUES
    (2001, '小米', '小米科技'),
    (2002, 'Apple', '苹果公司');

INSERT INTO t_spu (id, title, category_id, brand_id, description, main_image, images, status) VALUES
    (3001, '小米 14 Pro 5G 手机', 1002, 2001, '骁龙旗舰，徕卡影像',
     'https://picsum.photos/seed/mi14/400/400',
     '["https://picsum.photos/seed/mi14a/400/400","https://picsum.photos/seed/mi14b/400/400"]', 1);

INSERT INTO t_sku (id, spu_id, sku_code, specs, price, stock, locked_stock, image, sales, status) VALUES
    (4001, 3001, 'MI14-12-256', '{"颜色":"黑色","存储":"256GB"}', 4499.00, 200, 0, 'https://picsum.photos/seed/mi14/400/400', 36, 1),
    (4002, 3001, 'MI14-16-512', '{"颜色":"白色","存储":"512GB"}', 4999.00, 150, 0, 'https://picsum.photos/seed/mi14w/400/400', 21, 1);

INSERT INTO t_seckill_activity (id, spu_id, sku_id, title, price, stock, sold, start_time, end_time, status) VALUES
    (5001, 3001, 4001, '小米 14 Pro 秒杀', 3999.00, 20, 0,
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), 1);
