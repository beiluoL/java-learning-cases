-- 学习用示例库：执行前请先创建数据库
-- CREATE DATABASE learn_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_name`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户名',
    `age`         INT          NOT NULL DEFAULT 0 COMMENT '年龄',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：1=已删除，0=未删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_name` (`user_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 可选：插入一条示例数据
-- INSERT INTO `user` (`user_name`, `age`) VALUES ('alice', 20);
