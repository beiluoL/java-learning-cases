-- ============================================================
-- FinPay 支付脚手架 · 数据库初始化脚本（MySQL 8.0+）
-- 字符集 utf8mb4，金额统一使用 DECIMAL(18,2) 避免浮点误差
-- ============================================================
CREATE DATABASE IF NOT EXISTS finpay
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
USE finpay;

-- 商户表
CREATE TABLE IF NOT EXISTS t_merchant (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_no   VARCHAR(64)  NOT NULL COMMENT '商户号',
  name          VARCHAR(128) NOT NULL COMMENT '商户名称',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_no (merchant_no)
) ENGINE=InnoDB COMMENT='商户';

-- 支付订单表
CREATE TABLE IF NOT EXISTS t_payment_order (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  out_trade_no  VARCHAR(64)  NOT NULL COMMENT '商户订单号（幂等键）',
  trade_no      VARCHAR(64)  DEFAULT NULL COMMENT '渠道交易号',
  channel       VARCHAR(32)  NOT NULL COMMENT 'alipay/wechat',
  merchant_no   VARCHAR(64)  NOT NULL,
  amount        DECIMAL(18,2) NOT NULL COMMENT '金额（元）',
  currency      VARCHAR(8)   DEFAULT 'CNY',
  subject       VARCHAR(256) DEFAULT NULL,
  status        VARCHAR(32)  NOT NULL COMMENT 'PayStatus 枚举名',
  callback_raw  TEXT         COMMENT '回调原始报文（审计留痕）',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  pay_time      DATETIME     DEFAULT NULL,
  KEY idx_out_trade_no (out_trade_no),
  KEY idx_merchant_status (merchant_no, status),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='支付订单';

-- 退款订单表
CREATE TABLE IF NOT EXISTS t_refund_order (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  out_refund_no VARCHAR(64)  NOT NULL COMMENT '商户退款单号（幂等键）',
  out_trade_no  VARCHAR(64)  NOT NULL,
  trade_no      VARCHAR(64)  DEFAULT NULL,
  channel       VARCHAR(32)  NOT NULL,
  refund_amount DECIMAL(18,2) NOT NULL,
  reason        VARCHAR(256) DEFAULT NULL,
  status        VARCHAR(32)  NOT NULL,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_out_refund_no (out_refund_no)
) ENGINE=InnoDB COMMENT='退款订单';

-- 银行账户表（银行交易系统示例）
CREATE TABLE IF NOT EXISTS t_account (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_no    VARCHAR(64)  NOT NULL COMMENT '账户号',
  holder_name   VARCHAR(128) NOT NULL COMMENT '持卡人（加密存储）',
  card_no       VARCHAR(64)  NOT NULL COMMENT '卡号（加密存储）',
  balance       DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '可用余额',
  frozen        DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT 'TCC 冻结金额',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_account_no (account_no)
) ENGINE=InnoDB COMMENT='银行账户';

-- 银行交易流水表
CREATE TABLE IF NOT EXISTS t_bank_transaction (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tx_no         VARCHAR(64)  NOT NULL COMMENT '交易流水号',
  from_account  VARCHAR(64)  DEFAULT NULL,
  to_account    VARCHAR(64)  DEFAULT NULL,
  amount        DECIMAL(18,2) NOT NULL,
  type          VARCHAR(32)  NOT NULL COMMENT 'TRANSFER/DEPOSIT/WITHDRAW',
  status        VARCHAR(32)  NOT NULL COMMENT 'INIT/CONFIRMED/CANCELLED',
  branch_id     VARCHAR(64)  DEFAULT NULL COMMENT 'TCC 分支事务标识',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_branch_id (branch_id)
) ENGINE=InnoDB COMMENT='银行交易流水';

-- 操作审计日志表
CREATE TABLE IF NOT EXISTS t_audit_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator      VARCHAR(64)  NOT NULL,
  action        VARCHAR(64)  NOT NULL COMMENT '操作类型',
  resource      VARCHAR(64)  DEFAULT NULL,
  biz_id        VARCHAR(128) DEFAULT NULL,
  ip            VARCHAR(64)  DEFAULT NULL,
  channel       VARCHAR(32)  DEFAULT NULL,
  params        TEXT         COMMENT '请求参数摘要（已脱敏）',
  result        VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAIL',
  error         VARCHAR(512) DEFAULT NULL,
  occur_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_action (action),
  KEY idx_occur_time (occur_time)
) ENGINE=InnoDB COMMENT='操作审计日志';

-- 渠道配置表
CREATE TABLE IF NOT EXISTS t_channel_config (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel       VARCHAR(32)  NOT NULL,
  merchant_no   VARCHAR(64)  NOT NULL,
  app_id        VARCHAR(64)  DEFAULT NULL,
  enabled       TINYINT      NOT NULL DEFAULT 1,
  config_json   TEXT         NOT NULL COMMENT '渠道私有参数（私钥/公钥/证书，加密存储）',
  sandbox       TINYINT      NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_channel_mch (channel, merchant_no)
) ENGINE=InnoDB COMMENT='渠道配置';

-- 对账批次表
CREATE TABLE IF NOT EXISTS t_reconcile_batch (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel       VARCHAR(32)  NOT NULL,
  biz_date      DATE         NOT NULL COMMENT '对账日期',
  local_count   INT          NOT NULL DEFAULT 0,
  channel_count INT          NOT NULL DEFAULT 0,
  matched_count INT          NOT NULL DEFAULT 0,
  mismatch_count INT         NOT NULL DEFAULT 0,
  status        VARCHAR(32)  NOT NULL DEFAULT 'DONE',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_biz_date (biz_date)
) ENGINE=InnoDB COMMENT='对账批次';

-- 对账差错明细表
CREATE TABLE IF NOT EXISTS t_reconcile_detail (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id      BIGINT       NOT NULL,
  out_trade_no  VARCHAR(64)  NOT NULL,
  mismatch_type VARCHAR(32)  NOT NULL COMMENT 'LOCAL_ONLY/CHANNEL_ONLY/AMOUNT_DIFF/STATUS_DIFF',
  description   VARCHAR(512) DEFAULT NULL,
  compensated   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已补偿',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_batch (batch_id)
) ENGINE=InnoDB COMMENT='对账差错明细';
