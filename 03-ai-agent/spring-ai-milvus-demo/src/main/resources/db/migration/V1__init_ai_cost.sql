-- AI Token Cost Platform · 基础表结构 (MySQL 8.0 / utf8mb4)
-- 约定：金额一律 DECIMAL(20,8) 且以 USD 结算；Token 一律 BIGINT；时间一律 UTC DATETIME(3)。
-- 所有表与关键字段均带 COMMENT，便于数据字典生成与运维排查。

CREATE TABLE ai_model_pricing
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    provider           VARCHAR(64)  NOT NULL COMMENT '模型供应商，如 openai / deepseek / mock / local',
    model_name         VARCHAR(128) NOT NULL COMMENT '模型名称（以供应商返回值为准）',
    model_type         VARCHAR(32)  NOT NULL DEFAULT 'CHAT' COMMENT '模型类型：CHAT / EMBEDDING / RERANK',
    input_price        DECIMAL(20, 8) NOT NULL COMMENT '每百万普通输入 Token 单价',
    output_price       DECIMAL(20, 8) NOT NULL COMMENT '每百万输出 Token 单价',
    cached_input_price DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '每百万缓存命中输入 Token 单价',
    currency           VARCHAR(8)   NOT NULL DEFAULT 'USD' COMMENT '结算货币，初版统一 USD',
    version            VARCHAR(64)  NOT NULL COMMENT '价格版本，账单保存快照',
    effective_from     DATETIME(3)  NOT NULL COMMENT '生效时间(UTC)',
    effective_to       DATETIME(3)  NULL COMMENT '失效时间(UTC)，NULL 表示长期有效',
    enabled            TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用 0 停用',
    demo_flag          TINYINT      NOT NULL DEFAULT 1 COMMENT '是否示例价格：1 表示 DEMO 报价，非供应商真实价格',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pricing_version (provider, model_name, version),
    KEY idx_pricing_lookup (provider, model_name, model_type, enabled, effective_from),
    KEY idx_pricing_effective (effective_from, effective_to)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 模型价格及历史版本';

CREATE TABLE ai_token_usage
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id          VARCHAR(64)  NOT NULL COMMENT '请求唯一 ID，幂等键',
    trace_id            VARCHAR(64)  NULL COMMENT '链路追踪 ID',
    user_id             VARCHAR(64)  NOT NULL COMMENT '用户 ID（服务端身份，不信任前端传值）',
    conversation_id     VARCHAR(64)  NULL COMMENT '会话 ID',
    business_scene      VARCHAR(32)  NOT NULL COMMENT '业务场景：CHAT / RAG / EMBEDDING / RERANK / QUERY_REWRITE / DOCUMENT_SUMMARY / AGENT',
    provider            VARCHAR(64)  NOT NULL COMMENT '模型供应商',
    model_name          VARCHAR(128) NOT NULL COMMENT '供应商实际返回的模型名',
    model_type          VARCHAR(32)  NOT NULL COMMENT '模型类型：CHAT / EMBEDDING / RERANK',
    input_tokens        BIGINT       NOT NULL DEFAULT 0 COMMENT '输入 Token（含缓存命中部分）',
    output_tokens       BIGINT       NOT NULL DEFAULT 0 COMMENT '输出 Token',
    total_tokens        BIGINT       NOT NULL DEFAULT 0 COMMENT '总 Token',
    cached_input_tokens BIGINT       NOT NULL DEFAULT 0 COMMENT '缓存命中的输入 Token（input_tokens 的子集，不重复计费）',
    reasoning_tokens    BIGINT       NOT NULL DEFAULT 0 COMMENT '推理 Token（按供应商规则，默认不单独计费）',
    usage_status        VARCHAR(16)  NOT NULL COMMENT 'Usage 来源：REAL / ESTIMATED / UNAVAILABLE / LOCAL / MOCK',
    request_status      VARCHAR(16)  NOT NULL COMMENT '请求状态：SUCCESS / FAILED / BUDGET_REJECTED / UNKNOWN',
    latency_ms          BIGINT       NOT NULL DEFAULT 0 COMMENT '模型调用耗时(毫秒)',
    input_cost          DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '普通输入成本',
    cached_input_cost   DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '缓存输入成本',
    output_cost         DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '输出成本',
    total_cost          DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '总成本',
    currency            VARCHAR(8)   NOT NULL DEFAULT 'USD' COMMENT '结算货币',
    pricing_version     VARCHAR(64)  NULL COMMENT '计费时命中的价格版本快照',
    pricing_id          BIGINT       NULL COMMENT '计费时命中的价格记录 ID 快照',
    error_code          VARCHAR(64)  NULL COMMENT '失败错误码',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调用时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_usage_request (request_id),
    KEY idx_usage_created (created_at),
    KEY idx_usage_user_scene (user_id, business_scene, created_at),
    KEY idx_usage_model (provider, model_name, created_at),
    KEY idx_usage_status_scene (request_status, usage_status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 调用明细与成本账单';

CREATE TABLE ai_budget_policy
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    budget_type        VARCHAR(24)  NOT NULL COMMENT '预算类型：GLOBAL / USER / MODEL / DAILY / MONTHLY',
    target_id          VARCHAR(128) NOT NULL DEFAULT '__ALL__' COMMENT '预算对象：用户 ID / 模型名 / __ALL__',
    daily_limit        DECIMAL(20, 8) NULL COMMENT '每日最大费用',
    monthly_limit      DECIMAL(20, 8) NULL COMMENT '每月最大费用',
    per_request_limit  DECIMAL(20, 8) NULL COMMENT '单次请求最大费用',
    max_input_tokens   BIGINT       NULL COMMENT '单次请求最大输入 Token',
    max_output_tokens  BIGINT       NULL COMMENT '单次请求最大输出 Token',
    allow_overrun      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否允许超额：0 严格拦截 1 允许并告警',
    enabled            TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用预算控制',
    currency           VARCHAR(8)   NOT NULL DEFAULT 'USD' COMMENT '结算货币',
    remark             VARCHAR(255) NULL COMMENT '备注',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_budget_scope (budget_type, target_id),
    KEY idx_budget_enabled (enabled, budget_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预算策略配置';

CREATE TABLE ai_budget_reservation
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id     VARCHAR(64)  NOT NULL COMMENT '请求唯一 ID',
    budget_type    VARCHAR(24)  NOT NULL COMMENT '预算类型',
    target_id      VARCHAR(128) NOT NULL COMMENT '预算对象',
    user_id        VARCHAR(64)  NOT NULL COMMENT '发起用户',
    reserved_amount DECIMAL(20, 8) NOT NULL COMMENT '预扣金额',
    settled_amount DECIMAL(20, 8) NULL COMMENT '实际结算金额',
    status         VARCHAR(16)  NOT NULL COMMENT 'RESERVED / SETTLED / RELEASED / UNKNOWN / EXPIRED',
    expire_at      DATETIME(3)  NULL COMMENT '预扣过期时间(UTC)',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_request (request_id),
    KEY idx_reservation_status_expire (status, expire_at),
    KEY idx_reservation_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预算预扣、结算与异常状态流水';

CREATE TABLE ai_cost_alert
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id   VARCHAR(64)  NULL COMMENT '关联请求 ID',
    alert_type   VARCHAR(32)  NOT NULL COMMENT 'BUDGET_EXCEEDED / COST_SPIKE / SETTLE_OVERRUN / UNKNOWN_USAGE / REDIS_FAILURE / RECONCILE_DIFF',
    budget_type  VARCHAR(24)  NULL COMMENT '关联预算类型',
    target_id    VARCHAR(128) NULL COMMENT '关联预算对象',
    message      VARCHAR(512) NOT NULL COMMENT '告警说明（不含敏感 Prompt 与密钥）',
    amount       DECIMAL(20, 8) NULL COMMENT '关联金额',
    severity     VARCHAR(16)  NOT NULL DEFAULT 'WARN' COMMENT 'INFO / WARN / ERROR',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '产生时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_alert_created (created_at),
    KEY idx_alert_type (alert_type, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预算预警与成本异常事件';
