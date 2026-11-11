-- V4 · 成本治理与优化平台 schema 扩展
-- 约定延续 V1：金额 DECIMAL(20,8) / Token BIGINT / 时间 UTC DATETIME(3) / 关键字段 COMMENT。
-- 说明：本版本只做「新增」，不删除、不改写 V1~V3 已落地的列与历史账单。

-- ============================================================
-- 1. ai_token_usage：补齐 Agent / 知识库 / 缓存 / 路由 / 重试归因维度
-- ============================================================
ALTER TABLE ai_token_usage
    ADD COLUMN agent_id         VARCHAR(64) NULL COMMENT 'Agent 标识（AGENT 场景的任务归属）' AFTER conversation_id,
    ADD COLUMN knowledge_base_id VARCHAR(64) NULL COMMENT '知识库标识（RAG 成本归因）' AFTER agent_id,
    ADD COLUMN cache_hit        TINYINT     NOT NULL DEFAULT 0 COMMENT '响应缓存命中：1 命中（未真实调用模型，成本按 0 记账）' AFTER request_status,
    ADD COLUMN cache_saved_cost DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '缓存命中节省成本（基于该请求上次真实账单，非新增账单）' AFTER total_cost,
    ADD COLUMN retry_count      INT         NOT NULL DEFAULT 0 COMMENT '模型调用重试次数',
    ADD COLUMN route_level      VARCHAR(8)  NULL COMMENT '模型路由等级：L1 低成本 / L2 常规 / L3 高能力',
    ADD KEY idx_usage_agent (agent_id, created_at),
    ADD KEY idx_usage_kb (knowledge_base_id, created_at);

-- ============================================================
-- 2. ai_budget_policy：补齐并发/请求数/Agent 上限与超额策略
-- ============================================================
ALTER TABLE ai_budget_policy
    ADD COLUMN max_requests     INT            NULL COMMENT '周期内最大请求数（NULL 表示不限）',
    ADD COLUMN max_concurrency  INT            NULL COMMENT '最大并发请求数（NULL 表示不限）',
    ADD COLUMN max_agent_steps  INT            NULL COMMENT 'Agent 任务最大执行步数',
    ADD COLUMN max_tool_calls   INT            NULL COMMENT 'Agent 任务最大工具调用次数',
    ADD COLUMN alert_threshold  DECIMAL(5, 4)  NULL COMMENT '预算使用率告警阈值（0~1）',
    ADD COLUMN overrun_policy   VARCHAR(16)    NOT NULL DEFAULT 'BLOCK' COMMENT '超额处理策略：BLOCK 阻断 / DOWNGRADE 降级 / ALLOW 记录并放行';

-- ============================================================
-- 3. ai_cost_alert：升级为可运营的告警中心（等级/对象/状态/去重/确认/恢复）
-- ============================================================
ALTER TABLE ai_cost_alert
    ADD COLUMN alert_level      VARCHAR(16)    NOT NULL DEFAULT 'WARNING' COMMENT '等级：INFO / WARNING / HIGH / CRITICAL / BLOCKED' AFTER alert_type,
    ADD COLUMN target_type      VARCHAR(24)    NULL COMMENT '告警对象类型：GLOBAL / USER / MODEL / SCENE / AGENT / KNOWLEDGE_BASE' AFTER alert_level,
    ADD COLUMN threshold_value  DECIMAL(20, 8) NULL COMMENT '触发阈值（预算金额或比率）',
    ADD COLUMN current_value    DECIMAL(20, 8) NULL COMMENT '触发时实际值',
    ADD COLUMN status           VARCHAR(16)    NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN / ACKNOWLEDGED / RESOLVED',
    ADD COLUMN occurrence_count INT            NOT NULL DEFAULT 1 COMMENT '相同指纹累计发生次数（去重合并）',
    ADD COLUMN fingerprint      VARCHAR(160)   NULL COMMENT '去重指纹（类型+对象+等级）',
    ADD COLUMN last_seen_at     DATETIME(3)    NULL COMMENT '最近一次发生时间(UTC)',
    ADD COLUMN acknowledged_at  DATETIME(3)    NULL COMMENT '确认时间(UTC)',
    ADD COLUMN acknowledged_by  VARCHAR(64)    NULL COMMENT '确认人',
    ADD COLUMN resolved_at      DATETIME(3)    NULL COMMENT '恢复时间(UTC)',
    ADD KEY idx_alert_fingerprint (fingerprint, status),
    ADD KEY idx_alert_status_level (status, alert_level, created_at);

-- ============================================================
-- 4. ai_cost_ledger：追加式成本账务流水（不可 UPDATE，冲正另起流水）
-- ============================================================
CREATE TABLE ai_cost_ledger
(
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    entry_no          VARCHAR(80)    NOT NULL COMMENT '账务流水号，幂等键',
    entry_type        VARCHAR(24)    NOT NULL COMMENT '类型：MODEL_CALL / EMBEDDING / RERANK / AGENT / INFRA_ALLOCATED / CACHE_SAVING / ADJUST',
    cost_kind         VARCHAR(16)    NOT NULL COMMENT '口径：ACTUAL 真实账单 / ALLOCATED 分摊 / SAVED 节省（不计入成本合计）',
    request_id        VARCHAR(64)    NULL COMMENT '关联请求 ID',
    trace_id          VARCHAR(64)    NULL COMMENT '链路追踪 ID',
    user_id           VARCHAR(64)    NULL COMMENT '用户 ID',
    agent_id          VARCHAR(64)    NULL COMMENT 'Agent 标识',
    knowledge_base_id VARCHAR(64)    NULL COMMENT '知识库标识',
    business_scene    VARCHAR(32)    NULL COMMENT '业务场景',
    provider          VARCHAR(64)    NULL COMMENT '供应商',
    model_name        VARCHAR(128)   NULL COMMENT '模型名',
    quantity          BIGINT         NULL COMMENT '计费量（Token 数或调用次数）',
    amount            DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '金额（正数为成本/节省，冲正为负数）',
    currency          VARCHAR(8)     NOT NULL DEFAULT 'USD' COMMENT '货币',
    pricing_version   VARCHAR(64)    NULL COMMENT '价格版本快照',
    remark            VARCHAR(255)   NULL COMMENT '备注（不含敏感内容）',
    occurred_at       DATETIME(3)    NOT NULL COMMENT '业务发生时间(UTC)',
    created_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入账时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_entry (entry_no),
    KEY idx_ledger_occurred (occurred_at),
    KEY idx_ledger_type (entry_type, occurred_at),
    KEY idx_ledger_user (user_id, occurred_at),
    KEY idx_ledger_scene (business_scene, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '追加式成本账务流水（历史不可修改）';

-- ============================================================
-- 5. ai_budget_transaction：预算变更流水（预扣/结算/释放/过期/调整）
-- ============================================================
CREATE TABLE ai_budget_transaction
(
    id             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    tx_no          VARCHAR(80)    NOT NULL COMMENT '流水号，幂等键',
    request_id     VARCHAR(64)    NULL COMMENT '关联请求 ID',
    budget_type    VARCHAR(24)    NOT NULL COMMENT '预算类型',
    target_id      VARCHAR(128)   NOT NULL COMMENT '预算对象',
    user_id        VARCHAR(64)    NULL COMMENT '用户',
    tx_type        VARCHAR(16)    NOT NULL COMMENT 'RESERVE / SETTLE / RELEASE / EXPIRE / ADJUST',
    amount         DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '本次变更金额（正=占用，负=归还）',
    balance_before DECIMAL(20, 8) NULL COMMENT '变更前已用金额（含预扣）',
    balance_after  DECIMAL(20, 8) NULL COMMENT '变更后已用金额（含预扣）',
    currency       VARCHAR(8)     NOT NULL DEFAULT 'USD' COMMENT '货币',
    remark         VARCHAR(255)   NULL COMMENT '备注',
    created_at     DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_budget_tx_no (tx_no),
    KEY idx_budget_tx_request (request_id),
    KEY idx_budget_tx_scope (budget_type, target_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预算变更流水（审计与对账依据）';

-- ============================================================
-- 6. ai_cost_protection_rule：自动保护规则
-- ============================================================
CREATE TABLE ai_cost_protection_rule
(
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_code       VARCHAR(64)    NOT NULL COMMENT '规则编码',
    rule_name       VARCHAR(128)   NOT NULL COMMENT '规则名称',
    protection_level TINYINT       NOT NULL COMMENT '生效保护等级 1~5',
    condition_type  VARCHAR(32)    NOT NULL COMMENT '触发条件：BUDGET_USAGE_RATE / DAILY_COST / ERROR_RATE / CACHE_HIT_RATE / MANUAL',
    threshold_value DECIMAL(20, 8) NULL COMMENT '条件阈值（比率为 0~1）',
    action          VARCHAR(32)    NOT NULL COMMENT '动作：NOTIFY / DOWNGRADE_MODEL / LIMIT_CONCURRENCY / DISABLE_AGENT / BLOCK_REQUEST / EMERGENCY_STOP',
    priority        INT            NOT NULL DEFAULT 100 COMMENT '优先级，数字越小越优先',
    enabled         TINYINT        NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark          VARCHAR(255)   NULL COMMENT '说明',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    updated_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_protection_rule_code (rule_code),
    KEY idx_protection_rule_level (protection_level, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '自动成本保护规则';

-- ============================================================
-- 7. ai_cost_protection_log：保护动作与紧急开关操作日志
-- ============================================================
CREATE TABLE ai_cost_protection_log
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    action     VARCHAR(32)  NOT NULL COMMENT '动作：EMERGENCY_STOP / RESUME / LEVEL_CHANGE / DOWNGRADE / DISABLE_MODEL',
    from_level TINYINT      NULL COMMENT '变更前保护等级',
    to_level   TINYINT      NULL COMMENT '变更后保护等级',
    target_type VARCHAR(24) NULL COMMENT '作用对象类型：GLOBAL / PROVIDER / MODEL / AGENT / USER',
    target_id  VARCHAR(128) NULL COMMENT '作用对象 ID',
    operator   VARCHAR(64)  NOT NULL COMMENT '操作人（服务端身份或 system）',
    reason     VARCHAR(255) NOT NULL COMMENT '操作原因（必填，可审计）',
    detail     VARCHAR(512) NULL COMMENT '补充说明',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间(UTC)',
    restored_at DATETIME(3) NULL COMMENT '恢复时间(UTC)，NULL 表示仍在生效',
    PRIMARY KEY (id),
    KEY idx_protection_log_created (created_at),
    KEY idx_protection_log_action (action, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成本保护动作与紧急开关操作日志';

-- ============================================================
-- 8. ai_cost_optimization_log：成本优化执行记录与收益
-- ============================================================
CREATE TABLE ai_cost_optimization_log
(
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id    VARCHAR(64)    NULL COMMENT '关联请求 ID',
    strategy      VARCHAR(32)    NOT NULL COMMENT '策略：PROMPT_COMPRESSION / CONTEXT_COMPRESSION / RAG_CONTEXT_LIMIT / MODEL_ROUTING / RESPONSE_CACHE / EMBEDDING_DEDUP / OUTPUT_TOKEN_LIMIT / PROMPT_CACHING',
    before_tokens BIGINT         NULL COMMENT '优化前 Token（估算口径见 measure_kind）',
    after_tokens  BIGINT         NULL COMMENT '优化后 Token',
    saved_tokens  BIGINT         NULL COMMENT '节省 Token',
    saved_cost    DECIMAL(20, 8) NULL COMMENT '节省金额',
    measure_kind  VARCHAR(16)    NOT NULL COMMENT '口径：MEASURED 实测 / COUNTERFACTUAL 真实用量反推 / ESTIMATED 启发式估算',
    quality_note  VARCHAR(255)   NULL COMMENT '质量影响说明（未评测必须写明）',
    detail        VARCHAR(1024)  NULL COMMENT '执行明细（JSON 文本，不含敏感 Prompt 全文）',
    created_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_optimization_strategy (strategy, created_at),
    KEY idx_optimization_request (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成本优化执行记录与收益（区分实测/反推/估算）';

-- ============================================================
-- 9. ai_cost_daily_summary：每日成本汇总（避免统计接口全表扫描）
-- ============================================================
CREATE TABLE ai_cost_daily_summary
(
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    summary_date    DATE           NOT NULL COMMENT '汇总日期(UTC)',
    dimension       VARCHAR(16)    NOT NULL COMMENT '维度：ALL / MODEL / SCENE / USER / AGENT',
    dimension_key   VARCHAR(128)   NOT NULL DEFAULT '__ALL__' COMMENT '维度取值',
    request_count   BIGINT         NOT NULL DEFAULT 0 COMMENT '请求次数',
    success_count   BIGINT         NOT NULL DEFAULT 0 COMMENT '成功次数',
    failed_count    BIGINT         NOT NULL DEFAULT 0 COMMENT '失败次数',
    input_tokens    BIGINT         NOT NULL DEFAULT 0 COMMENT '输入 Token',
    output_tokens   BIGINT         NOT NULL DEFAULT 0 COMMENT '输出 Token',
    cached_tokens   BIGINT         NOT NULL DEFAULT 0 COMMENT '缓存命中输入 Token',
    total_tokens    BIGINT         NOT NULL DEFAULT 0 COMMENT '总 Token',
    total_cost      DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '总成本',
    cache_saved_cost DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '缓存节省成本',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    updated_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_summary (summary_date, dimension, dimension_key),
    KEY idx_daily_summary_date (summary_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '每日成本汇总（按维度预聚合）';

-- ============================================================
-- 10. ai_agent_cost_trace：Agent 任务级成本追踪
-- ============================================================
CREATE TABLE ai_agent_cost_trace
(
    id             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id        VARCHAR(64)    NOT NULL COMMENT 'Agent 任务 ID，幂等键',
    agent_id       VARCHAR(64)    NOT NULL COMMENT 'Agent 标识',
    request_id     VARCHAR(64)    NULL COMMENT '最近一次模型请求 ID',
    user_id        VARCHAR(64)    NOT NULL COMMENT '发起用户',
    conversation_id VARCHAR(64)   NULL COMMENT '会话 ID',
    steps          INT            NOT NULL DEFAULT 0 COMMENT '已执行步数',
    tool_calls     INT            NOT NULL DEFAULT 0 COMMENT '工具调用次数',
    input_tokens   BIGINT         NOT NULL DEFAULT 0 COMMENT '累计输入 Token',
    output_tokens  BIGINT         NOT NULL DEFAULT 0 COMMENT '累计输出 Token',
    total_tokens   BIGINT         NOT NULL DEFAULT 0 COMMENT '累计总 Token',
    total_cost     DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '累计成本',
    max_steps      INT            NULL COMMENT '步数上限',
    status         VARCHAR(16)    NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / COMPLETED / STOPPED / FAILED',
    stop_reason    VARCHAR(128)   NULL COMMENT '中止原因：STEP_LIMIT / COST_LIMIT / TOKEN_LIMIT / LOOP_DETECTED / MANUAL / ERROR',
    started_at     DATETIME(3)    NOT NULL COMMENT '开始时间(UTC)',
    finished_at    DATETIME(3)    NULL COMMENT '结束时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_task (task_id),
    KEY idx_agent_trace_user (user_id, started_at),
    KEY idx_agent_trace_status (status, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 任务级成本追踪';

-- ============================================================
-- 11. ai_infrastructure_cost：基础设施成本与分摊
-- ============================================================
CREATE TABLE ai_infrastructure_cost
(
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    resource_type     VARCHAR(32)    NOT NULL COMMENT '资源类型：SERVER / GPU / REDIS / MYSQL / MILVUS / OBJECT_STORAGE / NETWORK / MONITORING / LOCAL_MODEL',
    resource_name     VARCHAR(128)   NOT NULL COMMENT '资源名称',
    provider          VARCHAR(64)    NULL COMMENT '云厂商或 self-hosted',
    period_start      DATE           NOT NULL COMMENT '计费周期开始',
    period_end        DATE           NOT NULL COMMENT '计费周期结束',
    amount            DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '周期总成本',
    currency          VARCHAR(8)     NOT NULL DEFAULT 'USD' COMMENT '货币',
    allocation_method VARCHAR(24)    NOT NULL DEFAULT 'ALLOCATED' COMMENT 'METERED 直接计量 / ALLOCATED 按量分摊 / FIXED 固定成本',
    allocated_amount  DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '已分摊金额（不得超过 amount）',
    request_count     BIGINT         NULL COMMENT '分摊依据的请求量（用于计算单位分摊成本）',
    demo_flag         TINYINT        NOT NULL DEFAULT 1 COMMENT '1 表示 DEMO 数据，非真实云账单',
    remark            VARCHAR(255)   NULL COMMENT '备注',
    created_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_infra_period (period_start, period_end),
    KEY idx_infra_type (resource_type, period_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基础设施成本与分摊（区分直接计量与分摊）';
