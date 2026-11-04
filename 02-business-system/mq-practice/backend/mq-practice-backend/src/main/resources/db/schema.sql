-- =====================================================================================
--  H2 便携版建表脚本（默认 profile = h2，克隆即跑，无需安装 MySQL）
--  与 sql/01-schema-mysql.sql 列结构完全一致，仅去掉 H2 不兼容语法：
--    * ENGINE / CHARSET / COMMENT
--    * 内联 KEY 索引（改为独立 CREATE INDEX）
--    * ON UPDATE CURRENT_TIMESTAMP（改由 MyBatis-Plus MetaObjectHandler 维护）
--  H2 连接串：jdbc:h2:mem:mqpractice;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
-- =====================================================================================

DROP TABLE IF EXISTS mq_product;
CREATE TABLE mq_product
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_no  VARCHAR(32)    NOT NULL UNIQUE,
    name        VARCHAR(128)   NOT NULL,
    price       DECIMAL(12, 2) NOT NULL,
    status      TINYINT        NOT NULL DEFAULT 0,
    cover       VARCHAR(255)   DEFAULT NULL,
    description VARCHAR(512)   DEFAULT NULL,
    version     INT            NOT NULL DEFAULT 0,
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS mq_stock;
CREATE TABLE mq_stock
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT NOT NULL UNIQUE,
    total       INT    NOT NULL,
    available   INT    NOT NULL,
    version     INT    NOT NULL DEFAULT 0,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS mq_order;
CREATE TABLE mq_order
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no      VARCHAR(40)    NOT NULL UNIQUE,
    user_id       BIGINT         NOT NULL,
    product_id    BIGINT         NOT NULL,
    product_name  VARCHAR(128)   DEFAULT NULL,
    quantity      INT            NOT NULL DEFAULT 1,
    amount        DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    status        VARCHAR(20)    NOT NULL,
    expire_time   DATETIME       DEFAULT NULL,
    pay_time      DATETIME       DEFAULT NULL,
    cancel_time   DATETIME       DEFAULT NULL,
    cancel_reason VARCHAR(128)   DEFAULT NULL,
    delay_seconds INT            NOT NULL DEFAULT 1800,
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_order_user_status ON mq_order (user_id, status);
CREATE INDEX idx_order_status_expire ON mq_order (status, expire_time);
CREATE INDEX idx_order_create_time ON mq_order (create_time);

DROP TABLE IF EXISTS mq_idempotent_log;
CREATE TABLE mq_idempotent_log
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_key     VARCHAR(160) NOT NULL UNIQUE,
    scene       VARCHAR(64)  NOT NULL,
    message_id  VARCHAR(64)  DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    payload     TEXT,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_idem_scene_create ON mq_idempotent_log (scene, create_time);

DROP TABLE IF EXISTS mq_message_log;
CREATE TABLE mq_message_log
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id    VARCHAR(64)  NOT NULL UNIQUE,
    exchange_name VARCHAR(64)  DEFAULT NULL,
    routing_key   VARCHAR(64)  DEFAULT NULL,
    scene         VARCHAR(64)  DEFAULT NULL,
    biz_key       VARCHAR(160) DEFAULT NULL,
    payload       TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'SENDING',
    retry_count   INT          NOT NULL DEFAULT 0,
    error_msg     VARCHAR(512) DEFAULT NULL,
    cost_ms       BIGINT       DEFAULT NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_msg_scene_create ON mq_message_log (scene, create_time);
CREATE INDEX idx_msg_status ON mq_message_log (status);

DROP TABLE IF EXISTS mq_search_index;
CREATE TABLE mq_search_index
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT         NOT NULL UNIQUE,
    product_no  VARCHAR(32)    NOT NULL,
    name        VARCHAR(128)   NOT NULL,
    price       DECIMAL(12, 2) NOT NULL,
    status      TINYINT        NOT NULL DEFAULT 0,
    doc_version INT            NOT NULL DEFAULT 0,
    indexed_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_search_name ON mq_search_index (name);
