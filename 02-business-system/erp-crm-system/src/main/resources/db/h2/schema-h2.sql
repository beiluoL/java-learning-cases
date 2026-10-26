-- ============================================================================
--  erp-crm-system H2 建表脚本（MySQL 兼容模式）
--  ---------------------------------------------------------------------------
--  【自动生成，请勿手工修改】
--  本文件由 scripts/gen_h2_schema.py 从 db/mysql/schema.sql 派生而来。
--  修改表结构请改 db/mysql/schema.sql，然后执行：
--      python3 scripts/gen_h2_schema.py
-- ============================================================================


-- ---------- 清理旧表（保证脚本可重复执行） ----------
DROP TABLE IF EXISTS crm_contract;
DROP TABLE IF EXISTS crm_follow;
DROP TABLE IF EXISTS crm_opportunity;
DROP TABLE IF EXISTS crm_contact;
DROP TABLE IF EXISTS crm_customer;
DROP TABLE IF EXISTS hr_salary;
DROP TABLE IF EXISTS hr_leave;
DROP TABLE IF EXISTS hr_attendance;
DROP TABLE IF EXISTS hr_employee;
DROP TABLE IF EXISTS inv_stock_record;
DROP TABLE IF EXISTS inv_stock;
DROP TABLE IF EXISTS inv_sales_order_item;
DROP TABLE IF EXISTS inv_sales_order;
DROP TABLE IF EXISTS inv_purchase_order_item;
DROP TABLE IF EXISTS inv_purchase_order;
DROP TABLE IF EXISTS inv_supplier;
DROP TABLE IF EXISTS inv_warehouse;
DROP TABLE IF EXISTS inv_product;
DROP TABLE IF EXISTS inv_category;
DROP TABLE IF EXISTS wf_record;
DROP TABLE IF EXISTS wf_task;
DROP TABLE IF EXISTS wf_instance;
DROP TABLE IF EXISTS wf_node;
DROP TABLE IF EXISTS wf_process;
DROP TABLE IF EXISTS sys_oper_log;
DROP TABLE IF EXISTS sys_dict_data;
DROP TABLE IF EXISTS sys_dict_type;
DROP TABLE IF EXISTS sys_role_dept;
DROP TABLE IF EXISTS sys_role_menu;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_menu;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_post;
DROP TABLE IF EXISTS sys_dept;

-- ---------- sys_dept ----------
CREATE TABLE sys_dept (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父部门 id（0=顶级，不建自引用外键）',
    ancestors VARCHAR(500) NOT NULL DEFAULT '' COMMENT '祖级 id 链路，逗号分隔，如 0,
    1,
    3',
    dept_name VARCHAR(50) NOT NULL COMMENT '部门名称',
    dept_code VARCHAR(50) DEFAULT NULL COMMENT '部门编码',
    order_num INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    leader_user_id BIGINT DEFAULT NULL COMMENT '部门负责人用户 id（审批流 DEPT_LEADER 策略依赖）',
    leader_name VARCHAR(50) DEFAULT NULL COMMENT '部门负责人姓名（冗余字段，避免列表关联查询）',
    phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    email VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_dept_parent ON sys_dept (parent_id);
CREATE INDEX idx_dept_leader ON sys_dept (leader_user_id);

-- ---------- sys_post ----------
CREATE TABLE sys_post (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    post_code VARCHAR(64) NOT NULL COMMENT '岗位编码',
    post_name VARCHAR(50) NOT NULL COMMENT '岗位名称',
    post_sort INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_post_code UNIQUE (post_code)
);

-- ---------- sys_user ----------
CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    username VARCHAR(50) NOT NULL COMMENT '登录账号',
    password VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt 加密存储，禁止明文）',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    nick_name VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    avatar VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    email VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    gender TINYINT NOT NULL DEFAULT 0 COMMENT '性别：0 未知 1 男 2 女',
    dept_id BIGINT DEFAULT NULL COMMENT '所属部门 id（逻辑外键 sys_dept.id）',
    post_id BIGINT DEFAULT NULL COMMENT '所属岗位 id（逻辑外键 sys_post.id）',
    user_type TINYINT NOT NULL DEFAULT 1 COMMENT '用户类型：0 系统内置 1 普通用户',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 停用',
    login_ip VARCHAR(64) DEFAULT NULL COMMENT '最后登录 IP',
    login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_user_username UNIQUE (username)
);
CREATE INDEX idx_user_dept ON sys_user (dept_id);
CREATE INDEX idx_user_post ON sys_user (post_id);
CREATE INDEX idx_user_phone ON sys_user (phone);

-- ---------- sys_role ----------
CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_key VARCHAR(64) NOT NULL COMMENT '角色标识（代码中用于鉴权，如 SALES_MANAGER）',
    role_sort INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    data_scope TINYINT NOT NULL DEFAULT 4 COMMENT '数据权限：1 全部 2 自定义部门 3 本部门 4 本部门及以下 5 仅本人',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_role_key UNIQUE (role_key)
);

-- ---------- sys_menu ----------
CREATE TABLE sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单 id（0=顶级）',
    order_num INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    path VARCHAR(200) DEFAULT NULL COMMENT '前端路由地址',
    component VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
    query_param VARCHAR(255) DEFAULT NULL COMMENT '路由携带参数',
    menu_type CHAR(1) NOT NULL COMMENT '菜单类型：M 目录 C 菜单 F 按钮',
    perms VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 inventory:product:add',
    icon VARCHAR(100) DEFAULT NULL COMMENT '菜单图标',
    is_frame TINYINT NOT NULL DEFAULT 0 COMMENT '是否外链：1 是 0 否',
    is_cache TINYINT NOT NULL DEFAULT 1 COMMENT '是否缓存页签：1 是 0 否',
    visible TINYINT NOT NULL DEFAULT 0 COMMENT '显示状态：0 显示 1 隐藏',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_menu_parent ON sys_menu (parent_id);
CREATE INDEX idx_menu_perms ON sys_menu (perms);

-- ---------- sys_user_role ----------
CREATE TABLE sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    user_id BIGINT NOT NULL COMMENT '用户 id（逻辑外键 sys_user.id）',
    role_id BIGINT NOT NULL COMMENT '角色 id（逻辑外键 sys_role.id）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);
CREATE INDEX idx_ur_role ON sys_user_role (role_id);

-- ---------- sys_role_menu ----------
CREATE TABLE sys_role_menu (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    role_id BIGINT NOT NULL COMMENT '角色 id（逻辑外键 sys_role.id）',
    menu_id BIGINT NOT NULL COMMENT '菜单 id（逻辑外键 sys_menu.id）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_role_menu UNIQUE (role_id, menu_id)
);
CREATE INDEX idx_rm_menu ON sys_role_menu (menu_id);

-- ---------- sys_role_dept ----------
CREATE TABLE sys_role_dept (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    role_id BIGINT NOT NULL COMMENT '角色 id（逻辑外键 sys_role.id）',
    dept_id BIGINT NOT NULL COMMENT '部门 id（逻辑外键 sys_dept.id）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_role_dept UNIQUE (role_id, dept_id)
);
CREATE INDEX idx_rd_dept ON sys_role_dept (dept_id);

-- ---------- sys_dict_type ----------
CREATE TABLE sys_dict_type (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    dict_name VARCHAR(100) NOT NULL COMMENT '字典名称',
    dict_type VARCHAR(100) NOT NULL COMMENT '字典类型编码',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_dict_type UNIQUE (dict_type)
);

-- ---------- sys_dict_data ----------
CREATE TABLE sys_dict_data (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    dict_type VARCHAR(100) NOT NULL COMMENT '字典类型编码（逻辑外键 sys_dict_type.dict_type）',
    dict_label VARCHAR(100) NOT NULL COMMENT '字典标签（展示值）',
    dict_value VARCHAR(100) NOT NULL COMMENT '字典键值（存储值）',
    dict_sort INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    list_class VARCHAR(100) DEFAULT NULL COMMENT '列表回显样式（success/warning/danger/info）',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认值：1 是 0 否',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_dict_data_type ON sys_dict_data (dict_type);

-- ---------- sys_oper_log ----------
CREATE TABLE sys_oper_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    title VARCHAR(100) NOT NULL COMMENT '操作模块',
    business_type TINYINT NOT NULL DEFAULT 0 COMMENT '业务类型：0 其它 1 新增 2 修改 3 删除 4 导出 5 导入 6 审批',
    method VARCHAR(255) DEFAULT NULL COMMENT '请求方法（类.方法名）',
    request_method VARCHAR(10) DEFAULT NULL COMMENT 'HTTP 请求方式',
    oper_user_id BIGINT DEFAULT NULL COMMENT '操作人用户 id',
    oper_name VARCHAR(50) DEFAULT NULL COMMENT '操作人账号',
    oper_dept_name VARCHAR(50) DEFAULT NULL COMMENT '操作人部门',
    oper_url VARCHAR(255) DEFAULT NULL COMMENT '请求 URL',
    oper_ip VARCHAR(64) DEFAULT NULL COMMENT '操作 IP',
    oper_param VARCHAR(20000) COMMENT '请求参数（已脱敏）',
    json_result VARCHAR(20000) COMMENT '返回结果摘要',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '操作状态：0 成功 1 失败',
    error_msg VARCHAR(2000) DEFAULT NULL COMMENT '异常信息',
    cost_time BIGINT NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    oper_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_log_oper_user ON sys_oper_log (oper_user_id);
CREATE INDEX idx_log_oper_time ON sys_oper_log (oper_time);
CREATE INDEX idx_log_business_type ON sys_oper_log (business_type);

-- ---------- wf_process ----------
CREATE TABLE wf_process (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    process_key VARCHAR(64) NOT NULL COMMENT '流程标识（代码中引用，如 PURCHASE_ORDER）',
    process_name VARCHAR(100) NOT NULL COMMENT '流程名称',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型（与 ProcessCallback 实现类的 businessType 对应）',
    category VARCHAR(50) DEFAULT NULL COMMENT '流程分类（采购/销售/人事/合同）',
    form_route VARCHAR(200) DEFAULT NULL COMMENT '前端表单详情路由，审批时跳转查看业务单据',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_process_key UNIQUE (process_key)
);
CREATE INDEX idx_process_business_type ON wf_process (business_type);

-- ---------- wf_node ----------
CREATE TABLE wf_node (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    process_id BIGINT NOT NULL COMMENT '流程定义 id（逻辑外键 wf_process.id）',
    node_key VARCHAR(64) NOT NULL COMMENT '节点标识',
    node_name VARCHAR(100) NOT NULL COMMENT '节点名称',
    node_order INT NOT NULL DEFAULT 1 COMMENT '节点顺序（从 1 开始，升序流转）',
    approver_type VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '审批人类型：USER/ROLE/DEPT_LEADER/INITIATOR_LEADER',
    approver_value VARCHAR(255) DEFAULT NULL COMMENT '审批人取值：USER 为用户 id 列表，ROLE 为角色标识列表，逗号分隔',
    approve_mode VARCHAR(16) NOT NULL DEFAULT 'ANY' COMMENT '多人审批策略：ALL 会签 / ANY 或签',
    condition_expr VARCHAR(500) DEFAULT NULL COMMENT 'SpEL 条件表达式，为空表示无条件进入，如 #amount > 10000',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_node_process ON wf_node (process_id);
CREATE INDEX idx_node_order ON wf_node (process_id, node_order);

-- ---------- wf_instance ----------
CREATE TABLE wf_instance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    instance_no VARCHAR(64) NOT NULL COMMENT '流程实例编号',
    process_id BIGINT NOT NULL COMMENT '流程定义 id（逻辑外键 wf_process.id）',
    process_key VARCHAR(64) NOT NULL COMMENT '流程标识（冗余，便于查询）',
    process_name VARCHAR(100) NOT NULL COMMENT '流程名称（冗余）',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型',
    business_id BIGINT NOT NULL COMMENT '业务单据 id',
    business_no VARCHAR(64) DEFAULT NULL COMMENT '业务单据编号',
    title VARCHAR(200) NOT NULL COMMENT '审批标题，如「张三提交的采购订单 PO202608150001」',
    form_data VARCHAR(20000) COMMENT '业务表单快照（JSON），供条件表达式求值与审批页展示',
    initiator_id BIGINT NOT NULL COMMENT '发起人用户 id',
    initiator_name VARCHAR(50) NOT NULL COMMENT '发起人姓名',
    initiator_dept_id BIGINT DEFAULT NULL COMMENT '发起人部门 id',
    current_node_id BIGINT DEFAULT NULL COMMENT '当前节点 id',
    current_node_name VARCHAR(100) DEFAULT NULL COMMENT '当前节点名称',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 审批中 1 已通过 2 已驳回 3 已撤销',
    submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_instance_no UNIQUE (instance_no)
);
CREATE INDEX idx_instance_business ON wf_instance (business_type, business_id);
CREATE INDEX idx_instance_initiator ON wf_instance (initiator_id);
CREATE INDEX idx_instance_status ON wf_instance (status);
CREATE INDEX idx_instance_process ON wf_instance (process_id);

-- ---------- wf_task ----------
CREATE TABLE wf_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    instance_id BIGINT NOT NULL COMMENT '流程实例 id（逻辑外键 wf_instance.id）',
    node_id BIGINT NOT NULL COMMENT '节点定义 id（逻辑外键 wf_node.id）',
    node_key VARCHAR(64) NOT NULL COMMENT '节点标识（冗余）',
    node_name VARCHAR(100) NOT NULL COMMENT '节点名称（冗余）',
    node_order INT NOT NULL DEFAULT 1 COMMENT '节点顺序（冗余）',
    approve_mode VARCHAR(16) NOT NULL DEFAULT 'ANY' COMMENT '多人审批策略（冗余）：ALL / ANY',
    approver_id BIGINT NOT NULL COMMENT '审批人用户 id',
    approver_name VARCHAR(50) NOT NULL COMMENT '审批人姓名',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 待处理 1 已通过 2 已驳回 3 已转办 4 已失效',
    approve_comment VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    approve_time DATETIME DEFAULT NULL COMMENT '审批时间',
    delegate_from_id BIGINT DEFAULT NULL COMMENT '转办来源用户 id（由谁转给我）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_task_instance ON wf_task (instance_id);
CREATE INDEX idx_task_approver ON wf_task (approver_id, status);
CREATE INDEX idx_task_node ON wf_task (node_id);

-- ---------- wf_record ----------
CREATE TABLE wf_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    instance_id BIGINT NOT NULL COMMENT '流程实例 id（逻辑外键 wf_instance.id）',
    node_id BIGINT DEFAULT NULL COMMENT '节点定义 id',
    node_name VARCHAR(100) DEFAULT NULL COMMENT '节点名称',
    operation VARCHAR(32) NOT NULL COMMENT '操作类型：SUBMIT/APPROVE/REJECT/CANCEL/TRANSFER/AUTO_PASS',
    operator_id BIGINT NOT NULL COMMENT '操作人用户 id',
    operator_name VARCHAR(50) NOT NULL COMMENT '操作人姓名',
    approve_comment VARCHAR(500) DEFAULT NULL COMMENT '意见',
    oper_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_record_instance ON wf_record (instance_id);
CREATE INDEX idx_record_operator ON wf_record (operator_id);

-- ---------- inv_category ----------
CREATE TABLE inv_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分类 id（0=顶级）',
    ancestors VARCHAR(500) NOT NULL DEFAULT '' COMMENT '祖级 id 链路，逗号分隔',
    category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
    category_code VARCHAR(64) DEFAULT NULL COMMENT '分类编码',
    order_num INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_category_parent ON inv_category (parent_id);

-- ---------- inv_product ----------
CREATE TABLE inv_product (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    product_code VARCHAR(64) NOT NULL COMMENT '商品编码',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    category_id BIGINT DEFAULT NULL COMMENT '分类 id（逻辑外键 inv_category.id）',
    category_name VARCHAR(100) DEFAULT NULL COMMENT '分类名称（冗余）',
    brand VARCHAR(100) DEFAULT NULL COMMENT '品牌',
    spec VARCHAR(200) DEFAULT NULL COMMENT '规格型号',
    unit VARCHAR(20) NOT NULL DEFAULT '个' COMMENT '计量单位',
    barcode VARCHAR(64) DEFAULT NULL COMMENT '条形码',
    purchase_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '采购参考价（金额禁用 FLOAT/DOUBLE）',
    sale_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '销售参考价',
    safety_stock INT NOT NULL DEFAULT 0 COMMENT '安全库存（低于该值触发库存预警）',
    image_url VARCHAR(255) DEFAULT NULL COMMENT '商品图片',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 上架 1 下架',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_product_code UNIQUE (product_code)
);
CREATE INDEX idx_product_category ON inv_product (category_id);
CREATE INDEX idx_product_name ON inv_product (product_name);

-- ---------- inv_warehouse ----------
CREATE TABLE inv_warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    warehouse_code VARCHAR(64) NOT NULL COMMENT '仓库编码',
    warehouse_name VARCHAR(100) NOT NULL COMMENT '仓库名称',
    address VARCHAR(255) DEFAULT NULL COMMENT '仓库地址',
    manager_id BIGINT DEFAULT NULL COMMENT '仓管员用户 id（逻辑外键 sys_user.id）',
    manager_name VARCHAR(50) DEFAULT NULL COMMENT '仓管员姓名（冗余）',
    phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_warehouse_code UNIQUE (warehouse_code)
);
CREATE INDEX idx_warehouse_manager ON inv_warehouse (manager_id);

-- ---------- inv_supplier ----------
CREATE TABLE inv_supplier (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    supplier_code VARCHAR(64) NOT NULL COMMENT '供应商编码',
    supplier_name VARCHAR(200) NOT NULL COMMENT '供应商名称',
    contact_person VARCHAR(50) DEFAULT NULL COMMENT '联系人',
    phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    email VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
    address VARCHAR(255) DEFAULT NULL COMMENT '地址',
    bank_name VARCHAR(100) DEFAULT NULL COMMENT '开户银行',
    bank_account VARCHAR(64) DEFAULT NULL COMMENT '银行账号',
    tax_no VARCHAR(64) DEFAULT NULL COMMENT '纳税人识别号',
    credit_level TINYINT NOT NULL DEFAULT 2 COMMENT '信用等级：1 优质 2 普通 3 观察',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 合作中 1 已停止合作',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_supplier_code UNIQUE (supplier_code)
);
CREATE INDEX idx_supplier_name ON inv_supplier (supplier_name);

-- ---------- inv_purchase_order ----------
CREATE TABLE inv_purchase_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    order_no VARCHAR(64) NOT NULL COMMENT '采购单号',
    supplier_id BIGINT NOT NULL COMMENT '供应商 id（逻辑外键 inv_supplier.id）',
    supplier_name VARCHAR(200) NOT NULL COMMENT '供应商名称（冗余）',
    warehouse_id BIGINT NOT NULL COMMENT '收货仓库 id（逻辑外键 inv_warehouse.id）',
    warehouse_name VARCHAR(100) NOT NULL COMMENT '收货仓库名称（冗余）',
    total_quantity INT NOT NULL DEFAULT 0 COMMENT '采购总数量',
    total_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '采购总金额',
    order_date DATE NOT NULL COMMENT '采购日期',
    expect_arrival_date DATE DEFAULT NULL COMMENT '预计到货日期',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 1 审批中 2 待入库 3 已入库 4 已驳回 5 已取消',
    audit_instance_id BIGINT DEFAULT NULL COMMENT '审批流实例 id（逻辑外键 wf_instance.id）',
    purchaser_id BIGINT NOT NULL COMMENT '采购员用户 id（数据权限归属人）',
    purchaser_name VARCHAR(50) NOT NULL COMMENT '采购员姓名',
    dept_id BIGINT DEFAULT NULL COMMENT '归属部门 id（数据权限过滤依据）',
    inbound_time DATETIME DEFAULT NULL COMMENT '入库时间',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_order_no UNIQUE (order_no)
);
CREATE INDEX idx_po_supplier ON inv_purchase_order (supplier_id);
CREATE INDEX idx_po_warehouse ON inv_purchase_order (warehouse_id);
CREATE INDEX idx_po_purchaser ON inv_purchase_order (purchaser_id);
CREATE INDEX idx_po_dept ON inv_purchase_order (dept_id);
CREATE INDEX idx_po_status ON inv_purchase_order (status);
CREATE INDEX idx_po_order_date ON inv_purchase_order (order_date);

-- ---------- inv_purchase_order_item ----------
CREATE TABLE inv_purchase_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    order_id BIGINT NOT NULL COMMENT '采购单 id（逻辑外键 inv_purchase_order.id）',
    order_no VARCHAR(64) NOT NULL COMMENT '采购单号（冗余）',
    product_id BIGINT NOT NULL COMMENT '商品 id（逻辑外键 inv_product.id）',
    product_code VARCHAR(64) NOT NULL COMMENT '商品编码（快照）',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
    spec VARCHAR(200) DEFAULT NULL COMMENT '规格型号（快照）',
    unit VARCHAR(20) DEFAULT NULL COMMENT '计量单位（快照）',
    quantity INT NOT NULL DEFAULT 0 COMMENT '采购数量',
    price DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '采购单价',
    amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '小计金额 = 数量 * 单价',
    received_quantity INT NOT NULL DEFAULT 0 COMMENT '已入库数量',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_poi_order ON inv_purchase_order_item (order_id);
CREATE INDEX idx_poi_product ON inv_purchase_order_item (product_id);

-- ---------- inv_sales_order ----------
CREATE TABLE inv_sales_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    order_no VARCHAR(64) NOT NULL COMMENT '销售单号',
    customer_id BIGINT NOT NULL COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name VARCHAR(200) NOT NULL COMMENT '客户名称（冗余）',
    warehouse_id BIGINT NOT NULL COMMENT '发货仓库 id（逻辑外键 inv_warehouse.id）',
    warehouse_name VARCHAR(100) NOT NULL COMMENT '发货仓库名称（冗余）',
    total_quantity INT NOT NULL DEFAULT 0 COMMENT '销售总数量',
    total_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '销售总金额',
    order_date DATE NOT NULL COMMENT '销售日期',
    expect_delivery_date DATE DEFAULT NULL COMMENT '预计发货日期',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 1 审批中 2 待出库 3 已出库 4 已驳回 5 已取消',
    audit_instance_id BIGINT DEFAULT NULL COMMENT '审批流实例 id（逻辑外键 wf_instance.id）',
    salesman_id BIGINT NOT NULL COMMENT '销售员用户 id（数据权限归属人）',
    salesman_name VARCHAR(50) NOT NULL COMMENT '销售员姓名',
    dept_id BIGINT DEFAULT NULL COMMENT '归属部门 id（数据权限过滤依据）',
    outbound_time DATETIME DEFAULT NULL COMMENT '出库时间',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_sales_order_no UNIQUE (order_no)
);
CREATE INDEX idx_so_customer ON inv_sales_order (customer_id);
CREATE INDEX idx_so_warehouse ON inv_sales_order (warehouse_id);
CREATE INDEX idx_so_salesman ON inv_sales_order (salesman_id);
CREATE INDEX idx_so_dept ON inv_sales_order (dept_id);
CREATE INDEX idx_so_status ON inv_sales_order (status);
CREATE INDEX idx_so_order_date ON inv_sales_order (order_date);

-- ---------- inv_sales_order_item ----------
CREATE TABLE inv_sales_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    order_id BIGINT NOT NULL COMMENT '销售单 id（逻辑外键 inv_sales_order.id）',
    order_no VARCHAR(64) NOT NULL COMMENT '销售单号（冗余）',
    product_id BIGINT NOT NULL COMMENT '商品 id（逻辑外键 inv_product.id）',
    product_code VARCHAR(64) NOT NULL COMMENT '商品编码（快照）',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
    spec VARCHAR(200) DEFAULT NULL COMMENT '规格型号（快照）',
    unit VARCHAR(20) DEFAULT NULL COMMENT '计量单位（快照）',
    quantity INT NOT NULL DEFAULT 0 COMMENT '销售数量',
    price DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '销售单价',
    amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '小计金额 = 数量 * 单价',
    delivered_quantity INT NOT NULL DEFAULT 0 COMMENT '已出库数量',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_soi_order ON inv_sales_order_item (order_id);
CREATE INDEX idx_soi_product ON inv_sales_order_item (product_id);

-- ---------- inv_stock ----------
CREATE TABLE inv_stock (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    product_id BIGINT NOT NULL COMMENT '商品 id（逻辑外键 inv_product.id）',
    warehouse_id BIGINT NOT NULL COMMENT '仓库 id（逻辑外键 inv_warehouse.id）',
    quantity INT NOT NULL DEFAULT 0 COMMENT '库存总数量',
    locked_quantity INT NOT NULL DEFAULT 0 COMMENT '锁定数量（已下单待出库）',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（@Version）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_product_warehouse UNIQUE (product_id, warehouse_id)
);
CREATE INDEX idx_stock_warehouse ON inv_stock (warehouse_id);

-- ---------- inv_stock_record ----------
CREATE TABLE inv_stock_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    record_no VARCHAR(64) NOT NULL COMMENT '流水单号',
    product_id BIGINT NOT NULL COMMENT '商品 id',
    product_code VARCHAR(64) DEFAULT NULL COMMENT '商品编码（快照）',
    product_name VARCHAR(200) DEFAULT NULL COMMENT '商品名称（快照）',
    warehouse_id BIGINT NOT NULL COMMENT '仓库 id',
    warehouse_name VARCHAR(100) DEFAULT NULL COMMENT '仓库名称（快照）',
    biz_type TINYINT NOT NULL COMMENT '业务类型：1 采购入库 2 销售出库 3 盘盈 4 盘亏 5 调拨入 6 调拨出',
    change_quantity INT NOT NULL COMMENT '变动数量（入库为正，出库为负）',
    before_quantity INT NOT NULL DEFAULT 0 COMMENT '变动前库存',
    after_quantity INT NOT NULL DEFAULT 0 COMMENT '变动后库存',
    source_type VARCHAR(64) DEFAULT NULL COMMENT '来源单据类型：PURCHASE_ORDER / SALES_ORDER / STOCK_CHECK',
    source_id BIGINT DEFAULT NULL COMMENT '来源单据 id',
    source_no VARCHAR(64) DEFAULT NULL COMMENT '来源单据编号',
    operator_id BIGINT DEFAULT NULL COMMENT '操作人用户 id',
    operator_name VARCHAR(50) DEFAULT NULL COMMENT '操作人姓名',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_sr_product ON inv_stock_record (product_id);
CREATE INDEX idx_sr_warehouse ON inv_stock_record (warehouse_id);
CREATE INDEX idx_sr_source ON inv_stock_record (source_type, source_id);
CREATE INDEX idx_sr_create_time ON inv_stock_record (create_time);
CREATE INDEX idx_sr_biz_type ON inv_stock_record (biz_type);

-- ---------- hr_employee ----------
CREATE TABLE hr_employee (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    employee_no VARCHAR(64) NOT NULL COMMENT '员工工号',
    user_id BIGINT DEFAULT NULL COMMENT '关联登录账号 id（逻辑外键 sys_user.id，可为空）',
    real_name VARCHAR(50) NOT NULL COMMENT '姓名',
    gender TINYINT NOT NULL DEFAULT 0 COMMENT '性别：0 未知 1 男 2 女',
    id_card VARCHAR(32) DEFAULT NULL COMMENT '身份证号（敏感信息，接口返回需脱敏）',
    birth_date DATE DEFAULT NULL COMMENT '出生日期',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    email VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
    dept_id BIGINT DEFAULT NULL COMMENT '所属部门 id（逻辑外键 sys_dept.id）',
    dept_name VARCHAR(50) DEFAULT NULL COMMENT '所属部门名称（冗余）',
    post_id BIGINT DEFAULT NULL COMMENT '岗位 id（逻辑外键 sys_post.id）',
    post_name VARCHAR(50) DEFAULT NULL COMMENT '岗位名称（冗余）',
    entry_date DATE DEFAULT NULL COMMENT '入职日期',
    leave_date DATE DEFAULT NULL COMMENT '离职日期',
    employ_status TINYINT NOT NULL DEFAULT 1 COMMENT '在职状态：1 正式 2 试用 3 离职',
    education VARCHAR(32) DEFAULT NULL COMMENT '学历',
    base_salary DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '基本薪资',
    emergency_contact VARCHAR(50) DEFAULT NULL COMMENT '紧急联系人',
    emergency_phone VARCHAR(20) DEFAULT NULL COMMENT '紧急联系电话',
    address VARCHAR(255) DEFAULT NULL COMMENT '居住地址',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_employee_no UNIQUE (employee_no)
);
CREATE INDEX idx_emp_user ON hr_employee (user_id);
CREATE INDEX idx_emp_dept ON hr_employee (dept_id);
CREATE INDEX idx_emp_status ON hr_employee (employ_status);
CREATE INDEX idx_emp_name ON hr_employee (real_name);

-- ---------- hr_attendance ----------
CREATE TABLE hr_attendance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    employee_id BIGINT NOT NULL COMMENT '员工 id（逻辑外键 hr_employee.id）',
    employee_no VARCHAR(64) DEFAULT NULL COMMENT '员工工号（冗余）',
    employee_name VARCHAR(50) DEFAULT NULL COMMENT '员工姓名（冗余）',
    dept_id BIGINT DEFAULT NULL COMMENT '部门 id（数据权限过滤依据）',
    dept_name VARCHAR(50) DEFAULT NULL COMMENT '部门名称（冗余）',
    attendance_date DATE NOT NULL COMMENT '考勤日期',
    check_in_time DATETIME DEFAULT NULL COMMENT '上班打卡时间',
    check_out_time DATETIME DEFAULT NULL COMMENT '下班打卡时间',
    work_hours DECIMAL(4, 1) NOT NULL DEFAULT 0.0 COMMENT '当日工时',
    attendance_status TINYINT NOT NULL DEFAULT 1 COMMENT '考勤状态：1 正常 2 迟到 3 早退 4 缺卡 5 旷工 6 请假 7 出差',
    late_minutes INT NOT NULL DEFAULT 0 COMMENT '迟到分钟数',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_attendance_emp_date UNIQUE (employee_id, attendance_date)
);
CREATE INDEX idx_att_dept ON hr_attendance (dept_id);
CREATE INDEX idx_att_date ON hr_attendance (attendance_date);
CREATE INDEX idx_att_status ON hr_attendance (attendance_status);

-- ---------- hr_leave ----------
CREATE TABLE hr_leave (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    leave_no VARCHAR(64) NOT NULL COMMENT '请假单号',
    employee_id BIGINT NOT NULL COMMENT '员工 id（逻辑外键 hr_employee.id）',
    employee_name VARCHAR(50) DEFAULT NULL COMMENT '员工姓名（冗余）',
    apply_user_id BIGINT DEFAULT NULL COMMENT '申请人用户 id（数据权限归属人）',
    dept_id BIGINT DEFAULT NULL COMMENT '部门 id（数据权限过滤依据）',
    dept_name VARCHAR(50) DEFAULT NULL COMMENT '部门名称（冗余）',
    leave_type TINYINT NOT NULL DEFAULT 1 COMMENT '请假类型：1 事假 2 病假 3 年假 4 调休 5 婚假 6 产假 7 丧假',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    leave_days DECIMAL(4, 1) NOT NULL DEFAULT 0.0 COMMENT '请假天数',
    reason VARCHAR(500) DEFAULT NULL COMMENT '请假事由',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 1 审批中 2 已通过 3 已驳回 4 已撤销',
    audit_instance_id BIGINT DEFAULT NULL COMMENT '审批流实例 id（逻辑外键 wf_instance.id）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_leave_no UNIQUE (leave_no)
);
CREATE INDEX idx_leave_employee ON hr_leave (employee_id);
CREATE INDEX idx_leave_dept ON hr_leave (dept_id);
CREATE INDEX idx_leave_status ON hr_leave (status);
CREATE INDEX idx_leave_apply_user ON hr_leave (apply_user_id);

-- ---------- hr_salary ----------
CREATE TABLE hr_salary (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    employee_id BIGINT NOT NULL COMMENT '员工 id（逻辑外键 hr_employee.id）',
    employee_no VARCHAR(64) DEFAULT NULL COMMENT '员工工号（冗余）',
    employee_name VARCHAR(50) DEFAULT NULL COMMENT '员工姓名（冗余）',
    dept_id BIGINT DEFAULT NULL COMMENT '部门 id（数据权限过滤依据）',
    dept_name VARCHAR(50) DEFAULT NULL COMMENT '部门名称（冗余）',
    salary_month CHAR(7) NOT NULL COMMENT '薪资月份，格式 yyyy-MM',
    base_salary DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '基本工资',
    performance_bonus DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '绩效奖金',
    allowance DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '补贴',
    overtime_pay DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '加班费',
    social_insurance DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '社保扣款',
    housing_fund DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '公积金扣款',
    personal_tax DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '个人所得税',
    other_deduction DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '其他扣款（迟到/事假等）',
    gross_salary DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '应发合计',
    net_salary DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '实发合计',
    pay_status TINYINT NOT NULL DEFAULT 0 COMMENT '发放状态：0 未发放 1 已发放',
    pay_time DATETIME DEFAULT NULL COMMENT '发放时间',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_salary_emp_month UNIQUE (employee_id, salary_month)
);
CREATE INDEX idx_salary_dept ON hr_salary (dept_id);
CREATE INDEX idx_salary_month ON hr_salary (salary_month);
CREATE INDEX idx_salary_pay_status ON hr_salary (pay_status);

-- ---------- crm_customer ----------
CREATE TABLE crm_customer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    customer_code VARCHAR(64) NOT NULL COMMENT '客户编号',
    customer_name VARCHAR(200) NOT NULL COMMENT '客户名称',
    customer_type TINYINT NOT NULL DEFAULT 1 COMMENT '客户类型：1 企业 2 个人',
    industry VARCHAR(64) DEFAULT NULL COMMENT '所属行业',
    source TINYINT NOT NULL DEFAULT 5 COMMENT '客户来源：1 网络推广 2 电话营销 3 客户转介绍 4 展会 5 其他',
    customer_level TINYINT NOT NULL DEFAULT 2 COMMENT '客户级别：1 重要客户 2 普通客户 3 潜在客户',
    region VARCHAR(100) DEFAULT NULL COMMENT '所在地区',
    address VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
    website VARCHAR(200) DEFAULT NULL COMMENT '公司网址',
    phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    credit_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '信用额度',
    owner_id BIGINT DEFAULT NULL COMMENT '负责人用户 id（数据权限归属人）',
    owner_name VARCHAR(50) DEFAULT NULL COMMENT '负责人姓名（冗余）',
    dept_id BIGINT DEFAULT NULL COMMENT '归属部门 id（数据权限过滤依据）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '客户状态：1 潜在 2 跟进中 3 已成交 4 已流失',
    last_follow_time DATETIME DEFAULT NULL COMMENT '最近跟进时间',
    next_follow_time DATETIME DEFAULT NULL COMMENT '下次跟进时间（用于待跟进提醒）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_code UNIQUE (customer_code)
);
CREATE INDEX idx_customer_owner ON crm_customer (owner_id);
CREATE INDEX idx_customer_dept ON crm_customer (dept_id);
CREATE INDEX idx_customer_name ON crm_customer (customer_name);
CREATE INDEX idx_customer_status ON crm_customer (status);
CREATE INDEX idx_customer_source ON crm_customer (source);

-- ---------- crm_contact ----------
CREATE TABLE crm_contact (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    customer_id BIGINT NOT NULL COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name VARCHAR(200) DEFAULT NULL COMMENT '客户名称（冗余）',
    contact_name VARCHAR(50) NOT NULL COMMENT '联系人姓名',
    gender TINYINT NOT NULL DEFAULT 0 COMMENT '性别：0 未知 1 男 2 女',
    position VARCHAR(64) DEFAULT NULL COMMENT '职位',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    email VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
    wechat VARCHAR(64) DEFAULT NULL COMMENT '微信号',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主要联系人：1 是 0 否',
    owner_id BIGINT DEFAULT NULL COMMENT '负责人用户 id（数据权限归属人）',
    dept_id BIGINT DEFAULT NULL COMMENT '归属部门 id（数据权限过滤依据）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_contact_customer ON crm_contact (customer_id);
CREATE INDEX idx_contact_owner ON crm_contact (owner_id);
CREATE INDEX idx_contact_dept ON crm_contact (dept_id);

-- ---------- crm_opportunity ----------
CREATE TABLE crm_opportunity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    opportunity_no VARCHAR(64) NOT NULL COMMENT '商机编号',
    opportunity_name VARCHAR(200) NOT NULL COMMENT '商机名称',
    customer_id BIGINT NOT NULL COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name VARCHAR(200) DEFAULT NULL COMMENT '客户名称（冗余）',
    amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '预计成交金额',
    stage TINYINT NOT NULL DEFAULT 1 COMMENT '销售阶段：1 初步接洽 2 需求确认 3 方案报价 4 商务谈判 5 赢单 6 输单',
    win_rate INT NOT NULL DEFAULT 10 COMMENT '赢单概率（%）',
    expect_deal_date DATE DEFAULT NULL COMMENT '预计成交日期',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 进行中 1 已赢单 2 已输单',
    lose_reason VARCHAR(500) DEFAULT NULL COMMENT '输单原因',
    owner_id BIGINT DEFAULT NULL COMMENT '负责人用户 id（数据权限归属人）',
    owner_name VARCHAR(50) DEFAULT NULL COMMENT '负责人姓名（冗余）',
    dept_id BIGINT DEFAULT NULL COMMENT '归属部门 id（数据权限过滤依据）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_opportunity_no UNIQUE (opportunity_no)
);
CREATE INDEX idx_opp_customer ON crm_opportunity (customer_id);
CREATE INDEX idx_opp_owner ON crm_opportunity (owner_id);
CREATE INDEX idx_opp_dept ON crm_opportunity (dept_id);
CREATE INDEX idx_opp_stage ON crm_opportunity (stage);
CREATE INDEX idx_opp_status ON crm_opportunity (status);

-- ---------- crm_follow ----------
CREATE TABLE crm_follow (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    customer_id BIGINT NOT NULL COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name VARCHAR(200) DEFAULT NULL COMMENT '客户名称（冗余）',
    opportunity_id BIGINT DEFAULT NULL COMMENT '关联商机 id（可为空）',
    follow_type TINYINT NOT NULL DEFAULT 1 COMMENT '跟进方式：1 电话 2 上门拜访 3 邮件 4 微信 5 其他',
    follow_time DATETIME NOT NULL COMMENT '跟进时间',
    content VARCHAR(1000) NOT NULL COMMENT '跟进内容',
    next_follow_time DATETIME DEFAULT NULL COMMENT '下次跟进时间',
    owner_id BIGINT DEFAULT NULL COMMENT '跟进人用户 id（数据权限归属人）',
    owner_name VARCHAR(50) DEFAULT NULL COMMENT '跟进人姓名（冗余）',
    dept_id BIGINT DEFAULT NULL COMMENT '归属部门 id（数据权限过滤依据）',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
);
CREATE INDEX idx_follow_customer ON crm_follow (customer_id);
CREATE INDEX idx_follow_opportunity ON crm_follow (opportunity_id);
CREATE INDEX idx_follow_owner ON crm_follow (owner_id);
CREATE INDEX idx_follow_dept ON crm_follow (dept_id);
CREATE INDEX idx_follow_time ON crm_follow (follow_time);

-- ---------- crm_contract ----------
CREATE TABLE crm_contract (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    contract_no VARCHAR(64) NOT NULL COMMENT '合同编号',
    contract_name VARCHAR(200) NOT NULL COMMENT '合同名称',
    customer_id BIGINT NOT NULL COMMENT '客户 id（逻辑外键 crm_customer.id）',
    customer_name VARCHAR(200) DEFAULT NULL COMMENT '客户名称（冗余）',
    opportunity_id BIGINT DEFAULT NULL COMMENT '来源商机 id（可为空）',
    amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '合同金额',
    received_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '已回款金额',
    sign_date DATE DEFAULT NULL COMMENT '签约日期',
    start_date DATE DEFAULT NULL COMMENT '生效日期',
    end_date DATE DEFAULT NULL COMMENT '到期日期',
    payment_type TINYINT NOT NULL DEFAULT 1 COMMENT '付款方式：1 一次付清 2 分期付款 3 预付定金',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 1 审批中 2 已生效 3 已驳回 4 已作废 5 已完成',
    audit_instance_id BIGINT DEFAULT NULL COMMENT '审批流实例 id（逻辑外键 wf_instance.id）',
    owner_id BIGINT DEFAULT NULL COMMENT '负责人用户 id（数据权限归属人）',
    owner_name VARCHAR(50) DEFAULT NULL COMMENT '负责人姓名（冗余）',
    dept_id BIGINT DEFAULT NULL COMMENT '归属部门 id（数据权限过滤依据）',
    terms VARCHAR(2000) DEFAULT NULL COMMENT '合同条款',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    CONSTRAINT uk_contract_no UNIQUE (contract_no)
);
CREATE INDEX idx_contract_customer ON crm_contract (customer_id);
CREATE INDEX idx_contract_owner ON crm_contract (owner_id);
CREATE INDEX idx_contract_dept ON crm_contract (dept_id);
CREATE INDEX idx_contract_status ON crm_contract (status);
CREATE INDEX idx_contract_sign_date ON crm_contract (sign_date);
