-- ============================================================================
--  erp-crm-system 数据库结构脚本（MySQL 8.0）
--  ---------------------------------------------------------------------------
--  设计规约（《阿里巴巴 Java 开发手册》强制项）：
--   1. 不使用物理外键与级联，表间关联一律为「逻辑外键」，完整性由 Service 层保证；
--      所有逻辑外键列必须建普通索引。
--   2. 表名、字段名全小写，表名不用复数；主键 pk_、唯一索引 uk_、普通索引 idx_。
--   3. 表必备三字段：id（BIGINT 自增）、create_time、update_time（DATETIME）。
--   4. 统一逻辑删除字段 deleted（0 未删 / 1 已删），业务不做物理删除。
--   5. 金额一律 DECIMAL，禁止 FLOAT / DOUBLE。
--   6. 「是否」语义字段用 is_xxx + TINYINT(1)（POJO 属性去掉 is 前缀）。
--
--  表清单（35 张）
--   系统域   sys_*  : 用户/角色/菜单/部门/岗位/关联表/字典/操作日志        11 张
--   审批流域 wf_*   : 流程定义/节点定义/流程实例/审批任务/流转记录          5 张
--   进销存域 inv_*  : 分类/商品/仓库/供应商/采购单+明细/销售单+明细/库存+流水 10 张
--   人力域   hr_*   : 员工/考勤/请假/薪资                                  4 张
--   客户域   crm_*  : 客户/联系人/商机/跟进/合同                           5 张
-- ============================================================================

DROP DATABASE IF EXISTS erp_crm;
CREATE DATABASE erp_crm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE erp_crm;

-- ============================================================================
--  一、系统域（sys_*）—— RBAC 权限模型核心
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 部门表：树形结构，ancestors 存放祖级 id 链路（如 0,1,3），用于「本部门及子部门」数据权限
-- ---------------------------------------------------------------------------
CREATE TABLE sys_dept (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    parent_id       BIGINT       NOT NULL DEFAULT 0       COMMENT '父部门 id（0=顶级，不建自引用外键）',
    ancestors       VARCHAR(500) NOT NULL DEFAULT ''      COMMENT '祖级 id 链路，逗号分隔，如 0,1,3',
    dept_name       VARCHAR(50)  NOT NULL                 COMMENT '部门名称',
    dept_code       VARCHAR(50)           DEFAULT NULL    COMMENT '部门编码',
    order_num       INT          NOT NULL DEFAULT 0       COMMENT '显示顺序',
    leader_user_id  BIGINT                DEFAULT NULL    COMMENT '部门负责人用户 id（审批流 DEPT_LEADER 策略依赖）',
    leader_name     VARCHAR(50)           DEFAULT NULL    COMMENT '部门负责人姓名（冗余字段，避免列表关联查询）',
    phone           VARCHAR(20)           DEFAULT NULL    COMMENT '联系电话',
    email           VARCHAR(64)           DEFAULT NULL    COMMENT '邮箱',
    status          TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0 正常 1 停用',
    remark          VARCHAR(500)          DEFAULT NULL    COMMENT '备注',
    create_by       VARCHAR(64)           DEFAULT NULL    COMMENT '创建人',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64)           DEFAULT NULL    COMMENT '更新人',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         INT          NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_dept_parent (parent_id),
    KEY idx_dept_leader (leader_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部门表';

-- ---------------------------------------------------------------------------
-- 岗位表
-- ---------------------------------------------------------------------------
CREATE TABLE sys_post (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    post_code   VARCHAR(64) NOT NULL                COMMENT '岗位编码',
    post_name   VARCHAR(50) NOT NULL                COMMENT '岗位名称',
    post_sort   INT         NOT NULL DEFAULT 0      COMMENT '显示顺序',
    status      TINYINT     NOT NULL DEFAULT 0      COMMENT '状态：0 正常 1 停用',
    remark      VARCHAR(500)         DEFAULT NULL   COMMENT '备注',
    create_by   VARCHAR(64)          DEFAULT NULL   COMMENT '创建人',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)          DEFAULT NULL   COMMENT '更新人',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     INT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_code (post_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '岗位表';

-- ---------------------------------------------------------------------------
-- 用户表
-- ---------------------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    username    VARCHAR(50)  NOT NULL                COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL                COMMENT '登录密码（BCrypt 加密存储，禁止明文）',
    real_name   VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
    nick_name   VARCHAR(50)           DEFAULT NULL   COMMENT '昵称',
    avatar      VARCHAR(255)          DEFAULT NULL   COMMENT '头像地址',
    email       VARCHAR(64)           DEFAULT NULL   COMMENT '邮箱',
    phone       VARCHAR(20)           DEFAULT NULL   COMMENT '手机号',
    gender      TINYINT      NOT NULL DEFAULT 0      COMMENT '性别：0 未知 1 男 2 女',
    dept_id     BIGINT                DEFAULT NULL   COMMENT '所属部门 id（逻辑外键 sys_dept.id）',
    post_id     BIGINT                DEFAULT NULL   COMMENT '所属岗位 id（逻辑外键 sys_post.id）',
    user_type   TINYINT      NOT NULL DEFAULT 1      COMMENT '用户类型：0 系统内置 1 普通用户',
    status      TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0 正常 1 停用',
    login_ip    VARCHAR(64)           DEFAULT NULL   COMMENT '最后登录 IP',
    login_time  DATETIME              DEFAULT NULL   COMMENT '最后登录时间',
    remark      VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    create_by   VARCHAR(64)           DEFAULT NULL   COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_dept (dept_id),
    KEY idx_user_post (post_id),
    KEY idx_user_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ---------------------------------------------------------------------------
-- 角色表：data_scope 决定该角色的数据可见范围（数据权限核心字段）
-- ---------------------------------------------------------------------------
CREATE TABLE sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    role_name   VARCHAR(50) NOT NULL                COMMENT '角色名称',
    role_key    VARCHAR(64) NOT NULL                COMMENT '角色标识（代码中用于鉴权，如 SALES_MANAGER）',
    role_sort   INT         NOT NULL DEFAULT 0      COMMENT '显示顺序',
    data_scope  TINYINT     NOT NULL DEFAULT 4      COMMENT '数据权限：1 全部 2 自定义部门 3 本部门 4 本部门及以下 5 仅本人',
    status      TINYINT     NOT NULL DEFAULT 0      COMMENT '状态：0 正常 1 停用',
    remark      VARCHAR(500)         DEFAULT NULL   COMMENT '备注',
    create_by   VARCHAR(64)          DEFAULT NULL   COMMENT '创建人',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)          DEFAULT NULL   COMMENT '更新人',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     INT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_key (role_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- ---------------------------------------------------------------------------
-- 菜单/权限表：menu_type = M 目录 / C 菜单 / F 按钮，perms 为功能权限标识
-- ---------------------------------------------------------------------------
CREATE TABLE sys_menu (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    menu_name   VARCHAR(50) NOT NULL                COMMENT '菜单名称',
    parent_id   BIGINT      NOT NULL DEFAULT 0      COMMENT '父菜单 id（0=顶级）',
    order_num   INT         NOT NULL DEFAULT 0      COMMENT '显示顺序',
    path        VARCHAR(200)         DEFAULT NULL   COMMENT '前端路由地址',
    component   VARCHAR(255)         DEFAULT NULL   COMMENT '前端组件路径',
    query_param VARCHAR(255)         DEFAULT NULL   COMMENT '路由携带参数',
    menu_type   CHAR(1)     NOT NULL                COMMENT '菜单类型：M 目录 C 菜单 F 按钮',
    perms       VARCHAR(100)         DEFAULT NULL   COMMENT '权限标识，如 inventory:product:add',
    icon        VARCHAR(100)         DEFAULT NULL   COMMENT '菜单图标',
    is_frame    TINYINT     NOT NULL DEFAULT 0      COMMENT '是否外链：1 是 0 否',
    is_cache    TINYINT     NOT NULL DEFAULT 1      COMMENT '是否缓存页签：1 是 0 否',
    visible     TINYINT     NOT NULL DEFAULT 0      COMMENT '显示状态：0 显示 1 隐藏',
    status      TINYINT     NOT NULL DEFAULT 0      COMMENT '状态：0 正常 1 停用',
    remark      VARCHAR(500)         DEFAULT NULL   COMMENT '备注',
    create_by   VARCHAR(64)          DEFAULT NULL   COMMENT '创建人',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)          DEFAULT NULL   COMMENT '更新人',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     INT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_menu_parent (parent_id),
    KEY idx_menu_perms (perms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单权限表';

-- ---------------------------------------------------------------------------
-- 用户-角色关联表（多对多）
-- ---------------------------------------------------------------------------
CREATE TABLE sys_user_role (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    user_id     BIGINT   NOT NULL                COMMENT '用户 id（逻辑外键 sys_user.id）',
    role_id     BIGINT   NOT NULL                COMMENT '角色 id（逻辑外键 sys_role.id）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_ur_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户与角色关联表';

-- ---------------------------------------------------------------------------
-- 角色-菜单关联表（多对多）：决定功能权限
-- ---------------------------------------------------------------------------
CREATE TABLE sys_role_menu (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    role_id     BIGINT   NOT NULL                COMMENT '角色 id（逻辑外键 sys_role.id）',
    menu_id     BIGINT   NOT NULL                COMMENT '菜单 id（逻辑外键 sys_menu.id）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    KEY idx_rm_menu (menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色与菜单关联表';

-- ---------------------------------------------------------------------------
-- 角色-部门关联表：仅当 sys_role.data_scope = 2（自定义部门）时生效
-- ---------------------------------------------------------------------------
CREATE TABLE sys_role_dept (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    role_id     BIGINT   NOT NULL                COMMENT '角色 id（逻辑外键 sys_role.id）',
    dept_id     BIGINT   NOT NULL                COMMENT '部门 id（逻辑外键 sys_dept.id）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_dept (role_id, dept_id),
    KEY idx_rd_dept (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色与部门关联表（自定义数据权限）';

-- ---------------------------------------------------------------------------
-- 字典类型表 / 字典数据表：下拉枚举统一维护，避免魔法值散落前端
-- ---------------------------------------------------------------------------
CREATE TABLE sys_dict_type (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    dict_name   VARCHAR(100) NOT NULL               COMMENT '字典名称',
    dict_type   VARCHAR(100) NOT NULL               COMMENT '字典类型编码',
    status      TINYINT     NOT NULL DEFAULT 0      COMMENT '状态：0 正常 1 停用',
    remark      VARCHAR(500)         DEFAULT NULL   COMMENT '备注',
    create_by   VARCHAR(64)          DEFAULT NULL   COMMENT '创建人',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)          DEFAULT NULL   COMMENT '更新人',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     INT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type (dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典类型表';

CREATE TABLE sys_dict_data (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    dict_type   VARCHAR(100) NOT NULL                COMMENT '字典类型编码（逻辑外键 sys_dict_type.dict_type）',
    dict_label  VARCHAR(100) NOT NULL                COMMENT '字典标签（展示值）',
    dict_value  VARCHAR(100) NOT NULL                COMMENT '字典键值（存储值）',
    dict_sort   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    list_class  VARCHAR(100)          DEFAULT NULL   COMMENT '列表回显样式（success/warning/danger/info）',
    is_default  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认值：1 是 0 否',
    status      TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0 正常 1 停用',
    remark      VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    create_by   VARCHAR(64)           DEFAULT NULL   COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_dict_data_type (dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典数据表';

-- ---------------------------------------------------------------------------
-- 操作日志表：由 @OperLog 注解 + AOP 切面自动落库
-- ---------------------------------------------------------------------------
CREATE TABLE sys_oper_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    title          VARCHAR(100) NOT NULL                COMMENT '操作模块',
    business_type  TINYINT      NOT NULL DEFAULT 0      COMMENT '业务类型：0 其它 1 新增 2 修改 3 删除 4 导出 5 导入 6 审批',
    method         VARCHAR(255)          DEFAULT NULL   COMMENT '请求方法（类.方法名）',
    request_method VARCHAR(10)           DEFAULT NULL   COMMENT 'HTTP 请求方式',
    oper_user_id   BIGINT                DEFAULT NULL   COMMENT '操作人用户 id',
    oper_name      VARCHAR(50)           DEFAULT NULL   COMMENT '操作人账号',
    oper_dept_name VARCHAR(50)           DEFAULT NULL   COMMENT '操作人部门',
    oper_url       VARCHAR(255)          DEFAULT NULL   COMMENT '请求 URL',
    oper_ip        VARCHAR(64)           DEFAULT NULL   COMMENT '操作 IP',
    oper_param     TEXT                                 COMMENT '请求参数（已脱敏）',
    json_result    TEXT                                 COMMENT '返回结果摘要',
    status         TINYINT      NOT NULL DEFAULT 0      COMMENT '操作状态：0 成功 1 失败',
    error_msg      VARCHAR(2000)         DEFAULT NULL   COMMENT '异常信息',
    cost_time      BIGINT       NOT NULL DEFAULT 0      COMMENT '耗时（毫秒）',
    oper_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_log_oper_user (oper_user_id),
    KEY idx_log_oper_time (oper_time),
    KEY idx_log_business_type (business_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

-- ============================================================================
--  二、审批流域（wf_*）—— 轻量级审批流引擎
--  设计要点：
--    流程定义(wf_process) 1:N 节点定义(wf_node)
--    发起审批 → 生成流程实例(wf_instance) → 按节点顺序生成审批任务(wf_task)
--    每一次操作（提交/通过/驳回/撤销/转办）都写入流转记录(wf_record)，形成完整审批轨迹
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 流程定义表：business_type 是业务模块与流程的挂接点（回调 SPI 依据）
-- ---------------------------------------------------------------------------
CREATE TABLE wf_process (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    process_key   VARCHAR(64) NOT NULL                COMMENT '流程标识（代码中引用，如 PURCHASE_ORDER）',
    process_name  VARCHAR(100) NOT NULL               COMMENT '流程名称',
    business_type VARCHAR(64) NOT NULL                COMMENT '业务类型（与 ProcessCallback 实现类的 businessType 对应）',
    category      VARCHAR(50)          DEFAULT NULL   COMMENT '流程分类（采购/销售/人事/合同）',
    form_route    VARCHAR(200)         DEFAULT NULL   COMMENT '前端表单详情路由，审批时跳转查看业务单据',
    version       INT         NOT NULL DEFAULT 1      COMMENT '版本号',
    status        TINYINT     NOT NULL DEFAULT 0      COMMENT '状态：0 启用 1 停用',
    remark        VARCHAR(500)         DEFAULT NULL   COMMENT '备注',
    create_by     VARCHAR(64)          DEFAULT NULL   COMMENT '创建人',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     VARCHAR(64)          DEFAULT NULL   COMMENT '更新人',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       INT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_key (process_key),
    KEY idx_process_business_type (business_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批流程定义表';

-- ---------------------------------------------------------------------------
-- 流程节点定义表：node_order 决定流转顺序；condition_expr 为 SpEL 条件表达式
--   approver_type : USER 指定用户 / ROLE 指定角色 / DEPT_LEADER 发起人部门负责人
--                   / INITIATOR_LEADER 发起人上级部门负责人
--   approve_mode  : ALL 会签（全部通过才流转）/ ANY 或签（任一人通过即流转）
-- ---------------------------------------------------------------------------
CREATE TABLE wf_node (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    process_id     BIGINT      NOT NULL                COMMENT '流程定义 id（逻辑外键 wf_process.id）',
    node_key       VARCHAR(64) NOT NULL                COMMENT '节点标识',
    node_name      VARCHAR(100) NOT NULL               COMMENT '节点名称',
    node_order     INT         NOT NULL DEFAULT 1      COMMENT '节点顺序（从 1 开始，升序流转）',
    approver_type  VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '审批人类型：USER/ROLE/DEPT_LEADER/INITIATOR_LEADER',
    approver_value VARCHAR(255)         DEFAULT NULL   COMMENT '审批人取值：USER 为用户 id 列表，ROLE 为角色标识列表，逗号分隔',
    approve_mode   VARCHAR(16) NOT NULL DEFAULT 'ANY'  COMMENT '多人审批策略：ALL 会签 / ANY 或签',
    condition_expr VARCHAR(500)         DEFAULT NULL   COMMENT 'SpEL 条件表达式，为空表示无条件进入，如 #amount > 10000',
    remark         VARCHAR(500)         DEFAULT NULL   COMMENT '备注',
    create_by      VARCHAR(64)          DEFAULT NULL   COMMENT '创建人',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)          DEFAULT NULL   COMMENT '更新人',
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        INT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_node_process (process_id),
    KEY idx_node_order (process_id, node_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批流程节点定义表';

-- ---------------------------------------------------------------------------
-- 流程实例表：一次审批的运行态载体，business_id 指向具体业务单据
-- ---------------------------------------------------------------------------
CREATE TABLE wf_instance (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    instance_no       VARCHAR(64)  NOT NULL                COMMENT '流程实例编号',
    process_id        BIGINT       NOT NULL                COMMENT '流程定义 id（逻辑外键 wf_process.id）',
    process_key       VARCHAR(64)  NOT NULL                COMMENT '流程标识（冗余，便于查询）',
    process_name      VARCHAR(100) NOT NULL                COMMENT '流程名称（冗余）',
    business_type     VARCHAR(64)  NOT NULL                COMMENT '业务类型',
    business_id       BIGINT       NOT NULL                COMMENT '业务单据 id',
    business_no       VARCHAR(64)           DEFAULT NULL   COMMENT '业务单据编号',
    title             VARCHAR(200) NOT NULL                COMMENT '审批标题，如「张三提交的采购订单 PO202608150001」',
    form_data         TEXT                                 COMMENT '业务表单快照（JSON），供条件表达式求值与审批页展示',
    initiator_id      BIGINT       NOT NULL                COMMENT '发起人用户 id',
    initiator_name    VARCHAR(50)  NOT NULL                COMMENT '发起人姓名',
    initiator_dept_id BIGINT                DEFAULT NULL   COMMENT '发起人部门 id',
    current_node_id   BIGINT                DEFAULT NULL   COMMENT '当前节点 id',
    current_node_name VARCHAR(100)          DEFAULT NULL   COMMENT '当前节点名称',
    status            TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0 审批中 1 已通过 2 已驳回 3 已撤销',
    submit_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    end_time          DATETIME              DEFAULT NULL   COMMENT '结束时间',
    create_by         VARCHAR(64)           DEFAULT NULL   COMMENT '创建人',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_instance_no (instance_no),
    KEY idx_instance_business (business_type, business_id),
    KEY idx_instance_initiator (initiator_id),
    KEY idx_instance_status (status),
    KEY idx_instance_process (process_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批流程实例表';

-- ---------------------------------------------------------------------------
-- 审批任务表（待办）：一个节点的每个审批人对应一条任务
-- ---------------------------------------------------------------------------
CREATE TABLE wf_task (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    instance_id      BIGINT       NOT NULL                COMMENT '流程实例 id（逻辑外键 wf_instance.id）',
    node_id          BIGINT       NOT NULL                COMMENT '节点定义 id（逻辑外键 wf_node.id）',
    node_key         VARCHAR(64)  NOT NULL                COMMENT '节点标识（冗余）',
    node_name        VARCHAR(100) NOT NULL                COMMENT '节点名称（冗余）',
    node_order       INT          NOT NULL DEFAULT 1      COMMENT '节点顺序（冗余）',
    approve_mode     VARCHAR(16)  NOT NULL DEFAULT 'ANY'  COMMENT '多人审批策略（冗余）：ALL / ANY',
    approver_id      BIGINT       NOT NULL                COMMENT '审批人用户 id',
    approver_name    VARCHAR(50)  NOT NULL                COMMENT '审批人姓名',
    status           TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0 待处理 1 已通过 2 已驳回 3 已转办 4 已失效',
    approve_comment  VARCHAR(500)          DEFAULT NULL   COMMENT '审批意见',
    approve_time     DATETIME              DEFAULT NULL   COMMENT '审批时间',
    delegate_from_id BIGINT                DEFAULT NULL   COMMENT '转办来源用户 id（由谁转给我）',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_task_instance (instance_id),
    KEY idx_task_approver (approver_id, status),
    KEY idx_task_node (node_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批任务表（待办）';

-- ---------------------------------------------------------------------------
-- 流转记录表：审批轨迹，仅追加不修改
-- ---------------------------------------------------------------------------
CREATE TABLE wf_record (
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    instance_id     BIGINT      NOT NULL                COMMENT '流程实例 id（逻辑外键 wf_instance.id）',
    node_id         BIGINT               DEFAULT NULL   COMMENT '节点定义 id',
    node_name       VARCHAR(100)         DEFAULT NULL   COMMENT '节点名称',
    operation       VARCHAR(32) NOT NULL                COMMENT '操作类型：SUBMIT/APPROVE/REJECT/CANCEL/TRANSFER/AUTO_PASS',
    operator_id     BIGINT      NOT NULL                COMMENT '操作人用户 id',
    operator_name   VARCHAR(50) NOT NULL                COMMENT '操作人姓名',
    approve_comment VARCHAR(500)         DEFAULT NULL   COMMENT '意见',
    oper_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_record_instance (instance_id),
    KEY idx_record_operator (operator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批流转记录表';

-- ============================================================================
--  三、进销存域（inv_*）
--  核心业务闭环：
--    采购订单审批通过 → 入库 → inv_stock 库存增加 + inv_stock_record 流水
--    销售订单审批通过 → 出库 → 校验可用库存 → inv_stock 扣减 + 流水
--  库存表用 version 字段做乐观锁，避免并发出入库导致的超卖／数据错乱
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 商品分类表（树形）
-- ---------------------------------------------------------------------------
CREATE TABLE inv_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    parent_id     BIGINT       NOT NULL DEFAULT 0      COMMENT '父分类 id（0=顶级）',
    ancestors     VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '祖级 id 链路，逗号分隔',
    category_name VARCHAR(100) NOT NULL                COMMENT '分类名称',
    category_code VARCHAR(64)           DEFAULT NULL   COMMENT '分类编码',
    order_num     INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    status        TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0 正常 1 停用',
    remark        VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    create_by     VARCHAR(64)           DEFAULT NULL   COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_category_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类表';

-- ---------------------------------------------------------------------------
-- 商品表
-- ---------------------------------------------------------------------------
CREATE TABLE inv_product (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    product_code   VARCHAR(64)   NOT NULL                COMMENT '商品编码',
    product_name   VARCHAR(200)  NOT NULL                COMMENT '商品名称',
    category_id    BIGINT                 DEFAULT NULL   COMMENT '分类 id（逻辑外键 inv_category.id）',
    category_name  VARCHAR(100)           DEFAULT NULL   COMMENT '分类名称（冗余）',
    brand          VARCHAR(100)           DEFAULT NULL   COMMENT '品牌',
    spec           VARCHAR(200)           DEFAULT NULL   COMMENT '规格型号',
    unit           VARCHAR(20)   NOT NULL DEFAULT '个'   COMMENT '计量单位',
    barcode        VARCHAR(64)            DEFAULT NULL   COMMENT '条形码',
    purchase_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00  COMMENT '采购参考价（金额禁用 FLOAT/DOUBLE）',
    sale_price     DECIMAL(12, 2) NOT NULL DEFAULT 0.00  COMMENT '销售参考价',
    safety_stock   INT           NOT NULL DEFAULT 0      COMMENT '安全库存（低于该值触发库存预警）',
    image_url      VARCHAR(255)           DEFAULT NULL   COMMENT '商品图片',
    status         TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0 上架 1 下架',
    remark         VARCHAR(500)           DEFAULT NULL   COMMENT '备注',
    create_by      VARCHAR(64)            DEFAULT NULL   COMMENT '创建人',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)            DEFAULT NULL   COMMENT '更新人',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        INT           NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (product_code),
    KEY idx_product_category (category_id),
    KEY idx_product_name (product_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品表';

-- ---------------------------------------------------------------------------
-- 仓库表
-- ---------------------------------------------------------------------------
CREATE TABLE inv_warehouse (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    warehouse_code VARCHAR(64)  NOT NULL                COMMENT '仓库编码',
    warehouse_name VARCHAR(100) NOT NULL                COMMENT '仓库名称',
    address        VARCHAR(255)          DEFAULT NULL   COMMENT '仓库地址',
    manager_id     BIGINT                DEFAULT NULL   COMMENT '仓管员用户 id（逻辑外键 sys_user.id）',
    manager_name   VARCHAR(50)           DEFAULT NULL   COMMENT '仓管员姓名（冗余）',
    phone          VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
    status         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0 启用 1 停用',
    remark         VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    create_by      VARCHAR(64)           DEFAULT NULL   COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_code (warehouse_code),
    KEY idx_warehouse_manager (manager_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '仓库表';

-- ---------------------------------------------------------------------------
-- 供应商表
-- ---------------------------------------------------------------------------
CREATE TABLE inv_supplier (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    supplier_code  VARCHAR(64)  NOT NULL                COMMENT '供应商编码',
    supplier_name  VARCHAR(200) NOT NULL                COMMENT '供应商名称',
    contact_person VARCHAR(50)           DEFAULT NULL   COMMENT '联系人',
    phone          VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
    email          VARCHAR(64)           DEFAULT NULL   COMMENT '邮箱',
    address        VARCHAR(255)          DEFAULT NULL   COMMENT '地址',
    bank_name      VARCHAR(100)          DEFAULT NULL   COMMENT '开户银行',
    bank_account   VARCHAR(64)           DEFAULT NULL   COMMENT '银行账号',
    tax_no         VARCHAR(64)           DEFAULT NULL   COMMENT '纳税人识别号',
    credit_level   TINYINT      NOT NULL DEFAULT 2      COMMENT '信用等级：1 优质 2 普通 3 观察',
    status         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0 合作中 1 已停止合作',
    remark         VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    create_by      VARCHAR(64)           DEFAULT NULL   COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_code (supplier_code),
    KEY idx_supplier_name (supplier_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商表';

-- ---------------------------------------------------------------------------
-- 采购订单表
-- ---------------------------------------------------------------------------
CREATE TABLE inv_purchase_order (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    order_no           VARCHAR(64)    NOT NULL                COMMENT '采购单号',
    supplier_id        BIGINT         NOT NULL                COMMENT '供应商 id（逻辑外键 inv_supplier.id）',
    supplier_name      VARCHAR(200)   NOT NULL                COMMENT '供应商名称（冗余）',
    warehouse_id       BIGINT         NOT NULL                COMMENT '收货仓库 id（逻辑外键 inv_warehouse.id）',
    warehouse_name     VARCHAR(100)   NOT NULL                COMMENT '收货仓库名称（冗余）',
    total_quantity     INT            NOT NULL DEFAULT 0      COMMENT '采购总数量',
    total_amount       DECIMAL(14, 2) NOT NULL DEFAULT 0.00   COMMENT '采购总金额',
    order_date         DATE           NOT NULL                COMMENT '采购日期',
    expect_arrival_date DATE                   DEFAULT NULL   COMMENT '预计到货日期',
    status             TINYINT        NOT NULL DEFAULT 0      COMMENT '状态：0 草稿 1 审批中 2 待入库 3 已入库 4 已驳回 5 已取消',
    audit_instance_id  BIGINT                  DEFAULT NULL   COMMENT '审批流实例 id（逻辑外键 wf_instance.id）',
    purchaser_id       BIGINT         NOT NULL                COMMENT '采购员用户 id（数据权限归属人）',
    purchaser_name     VARCHAR(50)    NOT NULL                COMMENT '采购员姓名',
    dept_id            BIGINT                  DEFAULT NULL   COMMENT '归属部门 id（数据权限过滤依据）',
    inbound_time       DATETIME                DEFAULT NULL   COMMENT '入库时间',
    remark             VARCHAR(500)            DEFAULT NULL   COMMENT '备注',
    create_by          VARCHAR(64)             DEFAULT NULL   COMMENT '创建人',
    create_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by          VARCHAR(64)             DEFAULT NULL   COMMENT '更新人',
    update_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_order_no (order_no),
    KEY idx_po_supplier (supplier_id),
    KEY idx_po_warehouse (warehouse_id),
    KEY idx_po_purchaser (purchaser_id),
    KEY idx_po_dept (dept_id),
    KEY idx_po_status (status),
    KEY idx_po_order_date (order_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购订单表';

-- ---------------------------------------------------------------------------
-- 采购订单明细表
-- ---------------------------------------------------------------------------
CREATE TABLE inv_purchase_order_item (
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    order_id          BIGINT         NOT NULL                COMMENT '采购单 id（逻辑外键 inv_purchase_order.id）',
    order_no          VARCHAR(64)    NOT NULL                COMMENT '采购单号（冗余）',
    product_id        BIGINT         NOT NULL                COMMENT '商品 id（逻辑外键 inv_product.id）',
    product_code      VARCHAR(64)    NOT NULL                COMMENT '商品编码（快照）',
    product_name      VARCHAR(200)   NOT NULL                COMMENT '商品名称（快照）',
    spec              VARCHAR(200)            DEFAULT NULL   COMMENT '规格型号（快照）',
    unit              VARCHAR(20)             DEFAULT NULL   COMMENT '计量单位（快照）',
    quantity          INT            NOT NULL DEFAULT 0      COMMENT '采购数量',
    price             DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '采购单价',
    amount            DECIMAL(14, 2) NOT NULL DEFAULT 0.00   COMMENT '小计金额 = 数量 * 单价',
    received_quantity INT            NOT NULL DEFAULT 0      COMMENT '已入库数量',
    remark            VARCHAR(500)            DEFAULT NULL   COMMENT '备注',
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_poi_order (order_id),
    KEY idx_poi_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购订单明细表';

-- ---------------------------------------------------------------------------
-- 销售订单表
-- ---------------------------------------------------------------------------
CREATE TABLE inv_sales_order (
    id                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    order_no            VARCHAR(64)    NOT NULL                COMMENT '销售单号',
    customer_id         BIGINT         NOT NULL                COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name       VARCHAR(200)   NOT NULL                COMMENT '客户名称（冗余）',
    warehouse_id        BIGINT         NOT NULL                COMMENT '发货仓库 id（逻辑外键 inv_warehouse.id）',
    warehouse_name      VARCHAR(100)   NOT NULL                COMMENT '发货仓库名称（冗余）',
    total_quantity      INT            NOT NULL DEFAULT 0      COMMENT '销售总数量',
    total_amount        DECIMAL(14, 2) NOT NULL DEFAULT 0.00   COMMENT '销售总金额',
    order_date          DATE           NOT NULL                COMMENT '销售日期',
    expect_delivery_date DATE                   DEFAULT NULL   COMMENT '预计发货日期',
    status              TINYINT        NOT NULL DEFAULT 0      COMMENT '状态：0 草稿 1 审批中 2 待出库 3 已出库 4 已驳回 5 已取消',
    audit_instance_id   BIGINT                  DEFAULT NULL   COMMENT '审批流实例 id（逻辑外键 wf_instance.id）',
    salesman_id         BIGINT         NOT NULL                COMMENT '销售员用户 id（数据权限归属人）',
    salesman_name       VARCHAR(50)    NOT NULL                COMMENT '销售员姓名',
    dept_id             BIGINT                  DEFAULT NULL   COMMENT '归属部门 id（数据权限过滤依据）',
    outbound_time       DATETIME                DEFAULT NULL   COMMENT '出库时间',
    remark              VARCHAR(500)            DEFAULT NULL   COMMENT '备注',
    create_by           VARCHAR(64)             DEFAULT NULL   COMMENT '创建人',
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64)             DEFAULT NULL   COMMENT '更新人',
    update_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_no (order_no),
    KEY idx_so_customer (customer_id),
    KEY idx_so_warehouse (warehouse_id),
    KEY idx_so_salesman (salesman_id),
    KEY idx_so_dept (dept_id),
    KEY idx_so_status (status),
    KEY idx_so_order_date (order_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售订单表';

-- ---------------------------------------------------------------------------
-- 销售订单明细表
-- ---------------------------------------------------------------------------
CREATE TABLE inv_sales_order_item (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    order_id           BIGINT         NOT NULL                COMMENT '销售单 id（逻辑外键 inv_sales_order.id）',
    order_no           VARCHAR(64)    NOT NULL                COMMENT '销售单号（冗余）',
    product_id         BIGINT         NOT NULL                COMMENT '商品 id（逻辑外键 inv_product.id）',
    product_code       VARCHAR(64)    NOT NULL                COMMENT '商品编码（快照）',
    product_name       VARCHAR(200)   NOT NULL                COMMENT '商品名称（快照）',
    spec               VARCHAR(200)            DEFAULT NULL   COMMENT '规格型号（快照）',
    unit               VARCHAR(20)             DEFAULT NULL   COMMENT '计量单位（快照）',
    quantity           INT            NOT NULL DEFAULT 0      COMMENT '销售数量',
    price              DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '销售单价',
    amount             DECIMAL(14, 2) NOT NULL DEFAULT 0.00   COMMENT '小计金额 = 数量 * 单价',
    delivered_quantity INT            NOT NULL DEFAULT 0      COMMENT '已出库数量',
    remark             VARCHAR(500)            DEFAULT NULL   COMMENT '备注',
    create_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_soi_order (order_id),
    KEY idx_soi_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售订单明细表';

-- ---------------------------------------------------------------------------
-- 库存表：商品 + 仓库 唯一，version 做乐观锁防并发超卖
-- ---------------------------------------------------------------------------
CREATE TABLE inv_stock (
    id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    product_id      BIGINT   NOT NULL                COMMENT '商品 id（逻辑外键 inv_product.id）',
    warehouse_id    BIGINT   NOT NULL                COMMENT '仓库 id（逻辑外键 inv_warehouse.id）',
    quantity        INT      NOT NULL DEFAULT 0      COMMENT '库存总数量',
    locked_quantity INT      NOT NULL DEFAULT 0      COMMENT '锁定数量（已下单待出库）',
    version         INT      NOT NULL DEFAULT 0      COMMENT '乐观锁版本号（@Version）',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         INT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_product_warehouse (product_id, warehouse_id),
    KEY idx_stock_warehouse (warehouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存表';

-- ---------------------------------------------------------------------------
-- 库存流水表：每一次库存变动的不可篡改记录，用于对账与追溯
-- ---------------------------------------------------------------------------
CREATE TABLE inv_stock_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    record_no       VARCHAR(64)  NOT NULL                COMMENT '流水单号',
    product_id      BIGINT       NOT NULL                COMMENT '商品 id',
    product_code    VARCHAR(64)           DEFAULT NULL   COMMENT '商品编码（快照）',
    product_name    VARCHAR(200)          DEFAULT NULL   COMMENT '商品名称（快照）',
    warehouse_id    BIGINT       NOT NULL                COMMENT '仓库 id',
    warehouse_name  VARCHAR(100)          DEFAULT NULL   COMMENT '仓库名称（快照）',
    biz_type        TINYINT      NOT NULL                COMMENT '业务类型：1 采购入库 2 销售出库 3 盘盈 4 盘亏 5 调拨入 6 调拨出',
    change_quantity INT          NOT NULL                COMMENT '变动数量（入库为正，出库为负）',
    before_quantity INT          NOT NULL DEFAULT 0      COMMENT '变动前库存',
    after_quantity  INT          NOT NULL DEFAULT 0      COMMENT '变动后库存',
    source_type     VARCHAR(64)           DEFAULT NULL   COMMENT '来源单据类型：PURCHASE_ORDER / SALES_ORDER / STOCK_CHECK',
    source_id       BIGINT                DEFAULT NULL   COMMENT '来源单据 id',
    source_no       VARCHAR(64)           DEFAULT NULL   COMMENT '来源单据编号',
    operator_id     BIGINT                DEFAULT NULL   COMMENT '操作人用户 id',
    operator_name   VARCHAR(50)           DEFAULT NULL   COMMENT '操作人姓名',
    remark          VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sr_product (product_id),
    KEY idx_sr_warehouse (warehouse_id),
    KEY idx_sr_source (source_type, source_id),
    KEY idx_sr_create_time (create_time),
    KEY idx_sr_biz_type (biz_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存流水表';

-- ============================================================================
--  四、人力资源域（hr_*）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 员工档案表：与 sys_user 是「档案」与「登录账号」的一对一关系（user_id 可为空）
-- ---------------------------------------------------------------------------
CREATE TABLE hr_employee (
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    employee_no       VARCHAR(64)    NOT NULL                COMMENT '员工工号',
    user_id           BIGINT                  DEFAULT NULL   COMMENT '关联登录账号 id（逻辑外键 sys_user.id，可为空）',
    real_name         VARCHAR(50)    NOT NULL                COMMENT '姓名',
    gender            TINYINT        NOT NULL DEFAULT 0      COMMENT '性别：0 未知 1 男 2 女',
    id_card           VARCHAR(32)             DEFAULT NULL   COMMENT '身份证号（敏感信息，接口返回需脱敏）',
    birth_date        DATE                    DEFAULT NULL   COMMENT '出生日期',
    phone             VARCHAR(20)             DEFAULT NULL   COMMENT '手机号',
    email             VARCHAR(64)             DEFAULT NULL   COMMENT '邮箱',
    dept_id           BIGINT                  DEFAULT NULL   COMMENT '所属部门 id（逻辑外键 sys_dept.id）',
    dept_name         VARCHAR(50)             DEFAULT NULL   COMMENT '所属部门名称（冗余）',
    post_id           BIGINT                  DEFAULT NULL   COMMENT '岗位 id（逻辑外键 sys_post.id）',
    post_name         VARCHAR(50)             DEFAULT NULL   COMMENT '岗位名称（冗余）',
    entry_date        DATE                    DEFAULT NULL   COMMENT '入职日期',
    leave_date        DATE                    DEFAULT NULL   COMMENT '离职日期',
    employ_status     TINYINT        NOT NULL DEFAULT 1      COMMENT '在职状态：1 正式 2 试用 3 离职',
    education         VARCHAR(32)             DEFAULT NULL   COMMENT '学历',
    base_salary       DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '基本薪资',
    emergency_contact VARCHAR(50)             DEFAULT NULL   COMMENT '紧急联系人',
    emergency_phone   VARCHAR(20)             DEFAULT NULL   COMMENT '紧急联系电话',
    address           VARCHAR(255)            DEFAULT NULL   COMMENT '居住地址',
    remark            VARCHAR(500)            DEFAULT NULL   COMMENT '备注',
    create_by         VARCHAR(64)             DEFAULT NULL   COMMENT '创建人',
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)             DEFAULT NULL   COMMENT '更新人',
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_no (employee_no),
    KEY idx_emp_user (user_id),
    KEY idx_emp_dept (dept_id),
    KEY idx_emp_status (employ_status),
    KEY idx_emp_name (real_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '员工档案表';

-- ---------------------------------------------------------------------------
-- 考勤记录表：员工 + 日期 唯一
-- ---------------------------------------------------------------------------
CREATE TABLE hr_attendance (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    employee_id       BIGINT        NOT NULL                COMMENT '员工 id（逻辑外键 hr_employee.id）',
    employee_no       VARCHAR(64)            DEFAULT NULL   COMMENT '员工工号（冗余）',
    employee_name     VARCHAR(50)            DEFAULT NULL   COMMENT '员工姓名（冗余）',
    dept_id           BIGINT                 DEFAULT NULL   COMMENT '部门 id（数据权限过滤依据）',
    dept_name         VARCHAR(50)            DEFAULT NULL   COMMENT '部门名称（冗余）',
    attendance_date   DATE          NOT NULL                COMMENT '考勤日期',
    check_in_time     DATETIME               DEFAULT NULL   COMMENT '上班打卡时间',
    check_out_time    DATETIME               DEFAULT NULL   COMMENT '下班打卡时间',
    work_hours        DECIMAL(4, 1) NOT NULL DEFAULT 0.0    COMMENT '当日工时',
    attendance_status TINYINT       NOT NULL DEFAULT 1      COMMENT '考勤状态：1 正常 2 迟到 3 早退 4 缺卡 5 旷工 6 请假 7 出差',
    late_minutes      INT           NOT NULL DEFAULT 0      COMMENT '迟到分钟数',
    remark            VARCHAR(500)           DEFAULT NULL   COMMENT '备注',
    create_by         VARCHAR(64)            DEFAULT NULL   COMMENT '创建人',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)            DEFAULT NULL   COMMENT '更新人',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           INT           NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_emp_date (employee_id, attendance_date),
    KEY idx_att_dept (dept_id),
    KEY idx_att_date (attendance_date),
    KEY idx_att_status (attendance_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考勤记录表';

-- ---------------------------------------------------------------------------
-- 请假申请表：接入审批流（business_type = HR_LEAVE）
-- ---------------------------------------------------------------------------
CREATE TABLE hr_leave (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    leave_no          VARCHAR(64)   NOT NULL                COMMENT '请假单号',
    employee_id       BIGINT        NOT NULL                COMMENT '员工 id（逻辑外键 hr_employee.id）',
    employee_name     VARCHAR(50)            DEFAULT NULL   COMMENT '员工姓名（冗余）',
    apply_user_id     BIGINT                 DEFAULT NULL   COMMENT '申请人用户 id（数据权限归属人）',
    dept_id           BIGINT                 DEFAULT NULL   COMMENT '部门 id（数据权限过滤依据）',
    dept_name         VARCHAR(50)            DEFAULT NULL   COMMENT '部门名称（冗余）',
    leave_type        TINYINT       NOT NULL DEFAULT 1      COMMENT '请假类型：1 事假 2 病假 3 年假 4 调休 5 婚假 6 产假 7 丧假',
    start_time        DATETIME      NOT NULL                COMMENT '开始时间',
    end_time          DATETIME      NOT NULL                COMMENT '结束时间',
    leave_days        DECIMAL(4, 1) NOT NULL DEFAULT 0.0    COMMENT '请假天数',
    reason            VARCHAR(500)           DEFAULT NULL   COMMENT '请假事由',
    status            TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0 草稿 1 审批中 2 已通过 3 已驳回 4 已撤销',
    audit_instance_id BIGINT                 DEFAULT NULL   COMMENT '审批流实例 id（逻辑外键 wf_instance.id）',
    remark            VARCHAR(500)           DEFAULT NULL   COMMENT '备注',
    create_by         VARCHAR(64)            DEFAULT NULL   COMMENT '创建人',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)            DEFAULT NULL   COMMENT '更新人',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           INT           NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_no (leave_no),
    KEY idx_leave_employee (employee_id),
    KEY idx_leave_dept (dept_id),
    KEY idx_leave_status (status),
    KEY idx_leave_apply_user (apply_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '请假申请表';

-- ---------------------------------------------------------------------------
-- 薪资发放表：员工 + 月份 唯一
-- ---------------------------------------------------------------------------
CREATE TABLE hr_salary (
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    employee_id       BIGINT         NOT NULL                COMMENT '员工 id（逻辑外键 hr_employee.id）',
    employee_no       VARCHAR(64)             DEFAULT NULL   COMMENT '员工工号（冗余）',
    employee_name     VARCHAR(50)             DEFAULT NULL   COMMENT '员工姓名（冗余）',
    dept_id           BIGINT                  DEFAULT NULL   COMMENT '部门 id（数据权限过滤依据）',
    dept_name         VARCHAR(50)             DEFAULT NULL   COMMENT '部门名称（冗余）',
    salary_month      CHAR(7)        NOT NULL                COMMENT '薪资月份，格式 yyyy-MM',
    base_salary       DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '基本工资',
    performance_bonus DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '绩效奖金',
    allowance         DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '补贴',
    overtime_pay      DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '加班费',
    social_insurance  DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '社保扣款',
    housing_fund      DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '公积金扣款',
    personal_tax      DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '个人所得税',
    other_deduction   DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '其他扣款（迟到/事假等）',
    gross_salary      DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '应发合计',
    net_salary        DECIMAL(12, 2) NOT NULL DEFAULT 0.00   COMMENT '实发合计',
    pay_status        TINYINT        NOT NULL DEFAULT 0      COMMENT '发放状态：0 未发放 1 已发放',
    pay_time          DATETIME                DEFAULT NULL   COMMENT '发放时间',
    remark            VARCHAR(500)            DEFAULT NULL   COMMENT '备注',
    create_by         VARCHAR(64)             DEFAULT NULL   COMMENT '创建人',
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)             DEFAULT NULL   COMMENT '更新人',
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_salary_emp_month (employee_id, salary_month),
    KEY idx_salary_dept (dept_id),
    KEY idx_salary_month (salary_month),
    KEY idx_salary_pay_status (pay_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '薪资发放表';

-- ============================================================================
--  五、客户关系域（crm_*）
--  owner_id / dept_id 是数据权限的核心字段：销售只能看自己的客户，
--  销售经理能看本部门及下级部门，总经理看全部（由角色 data_scope 决定）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 客户表
-- ---------------------------------------------------------------------------
CREATE TABLE crm_customer (
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    customer_code    VARCHAR(64)    NOT NULL                COMMENT '客户编号',
    customer_name    VARCHAR(200)   NOT NULL                COMMENT '客户名称',
    customer_type    TINYINT        NOT NULL DEFAULT 1      COMMENT '客户类型：1 企业 2 个人',
    industry         VARCHAR(64)             DEFAULT NULL   COMMENT '所属行业',
    source           TINYINT        NOT NULL DEFAULT 5      COMMENT '客户来源：1 网络推广 2 电话营销 3 客户转介绍 4 展会 5 其他',
    customer_level   TINYINT        NOT NULL DEFAULT 2      COMMENT '客户级别：1 重要客户 2 普通客户 3 潜在客户',
    region           VARCHAR(100)            DEFAULT NULL   COMMENT '所在地区',
    address          VARCHAR(255)            DEFAULT NULL   COMMENT '详细地址',
    website          VARCHAR(200)            DEFAULT NULL   COMMENT '公司网址',
    phone            VARCHAR(20)             DEFAULT NULL   COMMENT '联系电话',
    credit_amount    DECIMAL(14, 2) NOT NULL DEFAULT 0.00   COMMENT '信用额度',
    owner_id         BIGINT                  DEFAULT NULL   COMMENT '负责人用户 id（数据权限归属人）',
    owner_name       VARCHAR(50)             DEFAULT NULL   COMMENT '负责人姓名（冗余）',
    dept_id          BIGINT                  DEFAULT NULL   COMMENT '归属部门 id（数据权限过滤依据）',
    status           TINYINT        NOT NULL DEFAULT 1      COMMENT '客户状态：1 潜在 2 跟进中 3 已成交 4 已流失',
    last_follow_time DATETIME                DEFAULT NULL   COMMENT '最近跟进时间',
    next_follow_time DATETIME                DEFAULT NULL   COMMENT '下次跟进时间（用于待跟进提醒）',
    remark           VARCHAR(500)            DEFAULT NULL   COMMENT '备注',
    create_by        VARCHAR(64)             DEFAULT NULL   COMMENT '创建人',
    create_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        VARCHAR(64)             DEFAULT NULL   COMMENT '更新人',
    update_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_code (customer_code),
    KEY idx_customer_owner (owner_id),
    KEY idx_customer_dept (dept_id),
    KEY idx_customer_name (customer_name),
    KEY idx_customer_status (status),
    KEY idx_customer_source (source)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户表';

-- ---------------------------------------------------------------------------
-- 客户联系人表
-- ---------------------------------------------------------------------------
CREATE TABLE crm_contact (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    customer_id   BIGINT       NOT NULL                COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name VARCHAR(200)          DEFAULT NULL   COMMENT '客户名称（冗余）',
    contact_name  VARCHAR(50)  NOT NULL                COMMENT '联系人姓名',
    gender        TINYINT      NOT NULL DEFAULT 0      COMMENT '性别：0 未知 1 男 2 女',
    position      VARCHAR(64)           DEFAULT NULL   COMMENT '职位',
    phone         VARCHAR(20)           DEFAULT NULL   COMMENT '手机号',
    email         VARCHAR(64)           DEFAULT NULL   COMMENT '邮箱',
    wechat        VARCHAR(64)           DEFAULT NULL   COMMENT '微信号',
    is_primary    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否主要联系人：1 是 0 否',
    owner_id      BIGINT                DEFAULT NULL   COMMENT '负责人用户 id（数据权限归属人）',
    dept_id       BIGINT                DEFAULT NULL   COMMENT '归属部门 id（数据权限过滤依据）',
    remark        VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    create_by     VARCHAR(64)           DEFAULT NULL   COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_contact_customer (customer_id),
    KEY idx_contact_owner (owner_id),
    KEY idx_contact_dept (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户联系人表';

-- ---------------------------------------------------------------------------
-- 商机表：stage 推进形成销售漏斗
-- ---------------------------------------------------------------------------
CREATE TABLE crm_opportunity (
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    opportunity_no   VARCHAR(64)    NOT NULL                COMMENT '商机编号',
    opportunity_name VARCHAR(200)   NOT NULL                COMMENT '商机名称',
    customer_id      BIGINT         NOT NULL                COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name    VARCHAR(200)            DEFAULT NULL   COMMENT '客户名称（冗余）',
    amount           DECIMAL(14, 2) NOT NULL DEFAULT 0.00   COMMENT '预计成交金额',
    stage            TINYINT        NOT NULL DEFAULT 1      COMMENT '销售阶段：1 初步接洽 2 需求确认 3 方案报价 4 商务谈判 5 赢单 6 输单',
    win_rate         INT            NOT NULL DEFAULT 10     COMMENT '赢单概率（%）',
    expect_deal_date DATE                    DEFAULT NULL   COMMENT '预计成交日期',
    status           TINYINT        NOT NULL DEFAULT 0      COMMENT '状态：0 进行中 1 已赢单 2 已输单',
    lose_reason      VARCHAR(500)            DEFAULT NULL   COMMENT '输单原因',
    owner_id         BIGINT                  DEFAULT NULL   COMMENT '负责人用户 id（数据权限归属人）',
    owner_name       VARCHAR(50)             DEFAULT NULL   COMMENT '负责人姓名（冗余）',
    dept_id          BIGINT                  DEFAULT NULL   COMMENT '归属部门 id（数据权限过滤依据）',
    remark           VARCHAR(500)            DEFAULT NULL   COMMENT '备注',
    create_by        VARCHAR(64)             DEFAULT NULL   COMMENT '创建人',
    create_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        VARCHAR(64)             DEFAULT NULL   COMMENT '更新人',
    update_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_opportunity_no (opportunity_no),
    KEY idx_opp_customer (customer_id),
    KEY idx_opp_owner (owner_id),
    KEY idx_opp_dept (dept_id),
    KEY idx_opp_stage (stage),
    KEY idx_opp_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商机表';

-- ---------------------------------------------------------------------------
-- 跟进记录表
-- ---------------------------------------------------------------------------
CREATE TABLE crm_follow (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    customer_id      BIGINT       NOT NULL                COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name    VARCHAR(200)          DEFAULT NULL   COMMENT '客户名称（冗余）',
    opportunity_id   BIGINT                DEFAULT NULL   COMMENT '关联商机 id（可为空）',
    follow_type      TINYINT      NOT NULL DEFAULT 1      COMMENT '跟进方式：1 电话 2 上门拜访 3 邮件 4 微信 5 其他',
    follow_time      DATETIME     NOT NULL                COMMENT '跟进时间',
    content          VARCHAR(1000) NOT NULL               COMMENT '跟进内容',
    next_follow_time DATETIME              DEFAULT NULL   COMMENT '下次跟进时间',
    owner_id         BIGINT                DEFAULT NULL   COMMENT '跟进人用户 id（数据权限归属人）',
    owner_name       VARCHAR(50)           DEFAULT NULL   COMMENT '跟进人姓名（冗余）',
    dept_id          BIGINT                DEFAULT NULL   COMMENT '归属部门 id（数据权限过滤依据）',
    create_by        VARCHAR(64)           DEFAULT NULL   COMMENT '创建人',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_follow_customer (customer_id),
    KEY idx_follow_opportunity (opportunity_id),
    KEY idx_follow_owner (owner_id),
    KEY idx_follow_dept (dept_id),
    KEY idx_follow_time (follow_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户跟进记录表';

-- ---------------------------------------------------------------------------
-- 合同表：接入审批流（business_type = CRM_CONTRACT）
-- ---------------------------------------------------------------------------
CREATE TABLE crm_contract (
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    contract_no       VARCHAR(64)    NOT NULL                COMMENT '合同编号',
    contract_name     VARCHAR(200)   NOT NULL                COMMENT '合同名称',
    customer_id       BIGINT         NOT NULL                COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name     VARCHAR(200)            DEFAULT NULL   COMMENT '客户名称（冗余）',
    opportunity_id    BIGINT                  DEFAULT NULL   COMMENT '来源商机 id（可为空）',
    amount            DECIMAL(14, 2) NOT NULL DEFAULT 0.00   COMMENT '合同金额',
    received_amount   DECIMAL(14, 2) NOT NULL DEFAULT 0.00   COMMENT '已回款金额',
    sign_date         DATE                    DEFAULT NULL   COMMENT '签约日期',
    start_date        DATE                    DEFAULT NULL   COMMENT '生效日期',
    end_date          DATE                    DEFAULT NULL   COMMENT '到期日期',
    payment_type      TINYINT        NOT NULL DEFAULT 1      COMMENT '付款方式：1 一次付清 2 分期付款 3 预付定金',
    status            TINYINT        NOT NULL DEFAULT 0      COMMENT '状态：0 草稿 1 审批中 2 已生效 3 已驳回 4 已作废 5 已完成',
    audit_instance_id BIGINT                  DEFAULT NULL   COMMENT '审批流实例 id（逻辑外键 wf_instance.id）',
    owner_id          BIGINT                  DEFAULT NULL   COMMENT '负责人用户 id（数据权限归属人）',
    owner_name        VARCHAR(50)             DEFAULT NULL   COMMENT '负责人姓名（冗余）',
    dept_id           BIGINT                  DEFAULT NULL   COMMENT '归属部门 id（数据权限过滤依据）',
    terms             VARCHAR(2000)           DEFAULT NULL   COMMENT '合同条款',
    remark            VARCHAR(500)            DEFAULT NULL   COMMENT '备注',
    create_by         VARCHAR(64)             DEFAULT NULL   COMMENT '创建人',
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         VARCHAR(64)             DEFAULT NULL   COMMENT '更新人',
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           INT            NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_no (contract_no),
    KEY idx_contract_customer (customer_id),
    KEY idx_contract_owner (owner_id),
    KEY idx_contract_dept (dept_id),
    KEY idx_contract_status (status),
    KEY idx_contract_sign_date (sign_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售合同表';
