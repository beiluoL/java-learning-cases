-- ============================================================
-- SSM-Learn 数据库初始化脚本
-- 执行方式：MySQL 客户端 / Navicat 等直接运行本文件
-- ============================================================

CREATE DATABASE IF NOT EXISTS ssm_learn
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE ssm_learn;

-- 系统用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL                COMMENT '用户名',
    password    VARCHAR(100) NOT NULL DEFAULT '123456' COMMENT '密码（演示用明文，生产务必加密）',
    real_name   VARCHAR(50)  DEFAULT ''              COMMENT '真实姓名',
    email       VARCHAR(100) DEFAULT ''              COMMENT '邮箱',
    phone       VARCHAR(20)  DEFAULT ''              COMMENT '手机号',
    status      TINYINT      DEFAULT 1               COMMENT '状态：1-启用 0-禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '系统用户表';

-- 初始化演示数据
INSERT INTO sys_user (username, real_name, email, phone, status) VALUES
    ('admin',   '管理员', 'admin@example.com',   '13800000000', 1),
    ('zhangsan','张三',   'zhang@example.com',   '13800000001', 1),
    ('lisi',    '李四',   'li@example.com',      '13800000002', 0);
