-- ============================================================================
--  erp-crm-system 初始化数据（MySQL 权威脚本，由 scripts/gen_data.py 自动合并）
--  ---------------------------------------------------------------------------
--  由 src/main/resources/db/mysql/_d1.sql ~ _d3.sql 按顺序合并生成，
--  请勿手工直接编辑本文件；修改请改分片后执行: python3 scripts/gen_data.py
--  演示账号（密码统一 123456，BCrypt 密文）：
--    admin     超级管理员      全部数据权限
--    zhangsan  销售经理        本部门及以下
--    lisi      销售专员        仅本人
--    wangwu    采购专员        本部门
--    zhaoliu   仓库管理员      本部门
--    sunqi     人事经理        全部
--    zhouba    销售二部专员    仅本人
-- ============================================================================

-- ============================================================================
--  erp-crm-system 初始化数据脚本
--  ---------------------------------------------------------------------------
--  内容：组织架构、账号角色、完整菜单权限树、字典、审批流定义、业务演示数据
--  演示账号（密码统一 123456，BCrypt 加密存储）：
--    admin     超级管理员      全部数据权限
--    zhangsan  销售经理        本部门及以下
--    lisi      销售专员        仅本人
--    wangwu    采购专员        本部门
--    zhaoliu   仓库管理员      本部门
--    sunqi     人事经理        全部
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 部门（组织架构）
-- ---------------------------------------------------------------------------
INSERT INTO sys_dept (id, parent_id, ancestors, dept_name, dept_code, order_num, leader_user_id, leader_name, phone, status) VALUES
(1,  0, '0',       '华南科技集团', 'HQ',        1, 1, '管理员',   '0755-88888888', 0),
(2,  1, '0,1',     '总经办',       'GM',        1, 1, '管理员',   '0755-88888801', 0),
(3,  1, '0,1',     '销售中心',     'SALES',     2, 2, '张三',     '0755-88888802', 0),
(4,  1, '0,1',     '采购中心',     'PURCHASE',  3, 4, '王五',     '0755-88888803', 0),
(5,  1, '0,1',     '仓储物流部',   'WAREHOUSE', 4, 5, '赵六',     '0755-88888804', 0),
(6,  1, '0,1',     '人力资源部',   'HR',        5, 6, '孙七',     '0755-88888805', 0),
(7,  1, '0,1',     '财务部',       'FINANCE',   6, NULL, NULL,    '0755-88888806', 0),
(8,  3, '0,1,3',   '销售一部',     'SALES_1',   1, 2, '张三',     '0755-88888811', 0),
(9,  3, '0,1,3',   '销售二部',     'SALES_2',   2, 2, '张三',     '0755-88888812', 0),
(10, 4, '0,1,4',   '采购一部',     'PURCHASE_1', 1, 4, '王五',    '0755-88888813', 0);

-- ---------------------------------------------------------------------------
-- 2. 岗位
-- ---------------------------------------------------------------------------
INSERT INTO sys_post (id, post_code, post_name, post_sort, status, remark) VALUES
(1, 'CEO',     '总经理',     1, 0, '公司最高管理者'),
(2, 'SALES_M', '销售经理',   2, 0, '销售团队负责人'),
(3, 'SALES_S', '销售专员',   3, 0, '一线销售'),
(4, 'PUR_S',   '采购专员',   4, 0, '负责采购执行'),
(5, 'WH_M',    '仓库管理员', 5, 0, '负责出入库作业'),
(6, 'HR_M',    '人事经理',   6, 0, '人力资源负责人'),
(7, 'FIN_S',   '财务专员',   7, 0, '负责账务处理');

-- ---------------------------------------------------------------------------
-- 3. 用户（密码均为 123456 的 BCrypt 密文）
-- ---------------------------------------------------------------------------
INSERT INTO sys_user (id, username, password, real_name, nick_name, email, phone, gender, dept_id, post_id, user_type, status, remark) VALUES
(1, 'admin',    '$2y$10$wWuCg8Sq53N1lZYKPgt2P.xz2Nd3kqJDOXq.xq1irZSsV2vK5NYi2', '管理员', '超级管理员', 'admin@erp.com',    '13800000001', 1, 2, 1, 0, 0, '系统内置超级管理员'),
(2, 'zhangsan', '$2y$10$wWuCg8Sq53N1lZYKPgt2P.xz2Nd3kqJDOXq.xq1irZSsV2vK5NYi2', '张三',   '张经理',     'zhangsan@erp.com', '13800000002', 1, 3, 2, 1, 0, '销售中心负责人'),
(3, 'lisi',     '$2y$10$wWuCg8Sq53N1lZYKPgt2P.xz2Nd3kqJDOXq.xq1irZSsV2vK5NYi2', '李四',   '小李',       'lisi@erp.com',     '13800000003', 1, 8, 3, 1, 0, '销售一部销售专员'),
(4, 'wangwu',   '$2y$10$wWuCg8Sq53N1lZYKPgt2P.xz2Nd3kqJDOXq.xq1irZSsV2vK5NYi2', '王五',   '老王',       'wangwu@erp.com',   '13800000004', 1, 4, 4, 1, 0, '采购中心采购专员'),
(5, 'zhaoliu',  '$2y$10$wWuCg8Sq53N1lZYKPgt2P.xz2Nd3kqJDOXq.xq1irZSsV2vK5NYi2', '赵六',   '六哥',       'zhaoliu@erp.com',  '13800000005', 1, 5, 5, 1, 0, '仓储物流部仓库管理员'),
(6, 'sunqi',    '$2y$10$wWuCg8Sq53N1lZYKPgt2P.xz2Nd3kqJDOXq.xq1irZSsV2vK5NYi2', '孙七',   '孙经理',     'sunqi@erp.com',    '13800000006', 2, 6, 6, 1, 0, '人力资源部负责人'),
(7, 'zhouba',   '$2y$10$wWuCg8Sq53N1lZYKPgt2P.xz2Nd3kqJDOXq.xq1irZSsV2vK5NYi2', '周八',   '小周',       'zhouba@erp.com',   '13800000007', 2, 9, 3, 1, 0, '销售二部销售专员');

-- ---------------------------------------------------------------------------
-- 4. 角色（data_scope：1 全部 2 自定义 3 本部门 4 本部门及以下 5 仅本人）
-- ---------------------------------------------------------------------------
INSERT INTO sys_role (id, role_name, role_key, role_sort, data_scope, status, remark) VALUES
(1, '超级管理员',   'SUPER_ADMIN',    1, 1, 0, '拥有全部功能权限与全部数据权限，不受权限校验限制'),
(2, '销售经理',     'SALES_MANAGER',  2, 4, 0, '可查看本部门及下级部门的销售数据'),
(3, '销售专员',     'SALES_STAFF',    3, 5, 0, '仅可查看自己负责的客户与订单'),
(4, '采购专员',     'PURCHASE_STAFF', 4, 3, 0, '可查看本部门采购数据'),
(5, '仓库管理员',   'WAREHOUSE_KEEP', 5, 1, 0, '负责出入库与库存管理'),
(6, '人事经理',     'HR_MANAGER',     6, 1, 0, '负责员工、考勤、薪资管理'),
(7, '财务专员',     'FINANCE_STAFF',  7, 2, 0, '自定义数据权限示例：仅可见销售中心与采购中心的数据');

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 2), (3, 3), (4, 4), (5, 5), (6, 6), (7, 3);

-- 自定义数据权限示例：财务专员（role_id=7）可见 销售中心 与 采购中心
INSERT INTO sys_role_dept (role_id, dept_id) VALUES (7, 3), (7, 4);

-- ---------------------------------------------------------------------------
-- 5. 菜单与权限树
--    id 编码规则：目录 10/20/... → 菜单 101/102/... → 按钮 10101/10102/...
--    菜单 path 为相对路径，前端拼接父级得到完整路由（如 system + user = /system/user）
--    menu_type：M 目录（无组件）/ C 菜单（对应页面组件）/ F 按钮（仅权限标识）
-- ---------------------------------------------------------------------------
INSERT INTO sys_menu (id, menu_name, parent_id, order_num, path, component, menu_type, perms, icon, is_frame, is_cache, visible, status) VALUES
-- ========== 一级 ==========
(1,  '驾驶舱',     0, 1,  'dashboard', 'dashboard/index', 'C', NULL, 'Odometer',     0, 1, 0, 0),
(50, '审批中心',   0, 2,  'workflow',  NULL,              'M', NULL, 'Stamp',        0, 1, 0, 0),
(20, '进销存管理', 0, 3,  'inventory', NULL,              'M', NULL, 'Goods',        0, 1, 0, 0),
(40, '客户关系',   0, 4,  'crm',       NULL,              'M', NULL, 'UserFilled',   0, 1, 0, 0),
(30, '人力资源',   0, 5,  'hrm',       NULL,              'M', NULL, 'User',         0, 1, 0, 0),
(60, '报表中心',   0, 6,  'report',    NULL,              'M', NULL, 'DataAnalysis', 0, 1, 0, 0),
(10, '系统管理',   0, 9,  'system',    NULL,              'M', NULL, 'Setting',      0, 1, 0, 0),

-- ========== 系统管理 ==========
(101, '用户管理', 10, 1, 'user', 'system/user/index', 'C', 'system:user:list', 'Avatar',         0, 1, 0, 0),
(102, '角色管理', 10, 2, 'role', 'system/role/index', 'C', 'system:role:list', 'Key',            0, 1, 0, 0),
(103, '菜单管理', 10, 3, 'menu', 'system/menu/index', 'C', 'system:menu:list', 'Menu',           0, 1, 0, 0),
(104, '部门管理', 10, 4, 'dept', 'system/dept/index', 'C', 'system:dept:list', 'OfficeBuilding', 0, 1, 0, 0),
(105, '岗位管理', 10, 5, 'post', 'system/post/index', 'C', 'system:post:list', 'Postcard',       0, 1, 0, 0),
(106, '字典管理', 10, 6, 'dict', 'system/dict/index', 'C', 'system:dict:list', 'Collection',     0, 1, 0, 0),
(107, '操作日志', 10, 7, 'log',  'system/log/index',  'C', 'system:log:list',  'Document',       0, 1, 0, 0),

(10101, '用户新增',   101, 1, NULL, NULL, 'F', 'system:user:add',        NULL, 0, 1, 0, 0),
(10102, '用户修改',   101, 2, NULL, NULL, 'F', 'system:user:edit',       NULL, 0, 1, 0, 0),
(10103, '用户删除',   101, 3, NULL, NULL, 'F', 'system:user:remove',     NULL, 0, 1, 0, 0),
(10104, '用户导出',   101, 4, NULL, NULL, 'F', 'system:user:export',     NULL, 0, 1, 0, 0),
(10105, '用户导入',   101, 5, NULL, NULL, 'F', 'system:user:import',     NULL, 0, 1, 0, 0),
(10106, '重置密码',   101, 6, NULL, NULL, 'F', 'system:user:resetPwd',   NULL, 0, 1, 0, 0),
(10107, '分配角色',   101, 7, NULL, NULL, 'F', 'system:user:assignRole', NULL, 0, 1, 0, 0),
(10201, '角色新增',   102, 1, NULL, NULL, 'F', 'system:role:add',        NULL, 0, 1, 0, 0),
(10202, '角色修改',   102, 2, NULL, NULL, 'F', 'system:role:edit',       NULL, 0, 1, 0, 0),
(10203, '角色删除',   102, 3, NULL, NULL, 'F', 'system:role:remove',     NULL, 0, 1, 0, 0),
(10204, '分配权限',   102, 4, NULL, NULL, 'F', 'system:role:assignPerm', NULL, 0, 1, 0, 0),
(10301, '菜单新增',   103, 1, NULL, NULL, 'F', 'system:menu:add',        NULL, 0, 1, 0, 0),
(10302, '菜单修改',   103, 2, NULL, NULL, 'F', 'system:menu:edit',       NULL, 0, 1, 0, 0),
(10303, '菜单删除',   103, 3, NULL, NULL, 'F', 'system:menu:remove',     NULL, 0, 1, 0, 0),
(10401, '部门新增',   104, 1, NULL, NULL, 'F', 'system:dept:add',        NULL, 0, 1, 0, 0),
(10402, '部门修改',   104, 2, NULL, NULL, 'F', 'system:dept:edit',       NULL, 0, 1, 0, 0),
(10403, '部门删除',   104, 3, NULL, NULL, 'F', 'system:dept:remove',     NULL, 0, 1, 0, 0),
(10501, '岗位新增',   105, 1, NULL, NULL, 'F', 'system:post:add',        NULL, 0, 1, 0, 0),
(10502, '岗位修改',   105, 2, NULL, NULL, 'F', 'system:post:edit',       NULL, 0, 1, 0, 0),
(10503, '岗位删除',   105, 3, NULL, NULL, 'F', 'system:post:remove',     NULL, 0, 1, 0, 0),
(10601, '字典新增',   106, 1, NULL, NULL, 'F', 'system:dict:add',        NULL, 0, 1, 0, 0),
(10602, '字典修改',   106, 2, NULL, NULL, 'F', 'system:dict:edit',       NULL, 0, 1, 0, 0),
(10603, '字典删除',   106, 3, NULL, NULL, 'F', 'system:dict:remove',     NULL, 0, 1, 0, 0),
(10701, '日志删除',   107, 1, NULL, NULL, 'F', 'system:log:remove',      NULL, 0, 1, 0, 0),
(10702, '日志导出',   107, 2, NULL, NULL, 'F', 'system:log:export',      NULL, 0, 1, 0, 0),

-- ========== 进销存管理 ==========
(201, '商品分类', 20, 1, 'category',     'inventory/category/index',    'C', 'inventory:category:list',    'Grid',      0, 1, 0, 0),
(202, '商品管理', 20, 2, 'product',      'inventory/product/index',     'C', 'inventory:product:list',     'ShoppingBag', 0, 1, 0, 0),
(203, '仓库管理', 20, 3, 'warehouse',    'inventory/warehouse/index',   'C', 'inventory:warehouse:list',   'House',     0, 1, 0, 0),
(204, '供应商管理', 20, 4, 'supplier',   'inventory/supplier/index',    'C', 'inventory:supplier:list',    'Van',       0, 1, 0, 0),
(205, '采购订单', 20, 5, 'purchase',     'inventory/purchase/index',    'C', 'inventory:purchase:list',    'Tickets',   0, 1, 0, 0),
(206, '销售订单', 20, 6, 'sales',        'inventory/sales/index',       'C', 'inventory:sales:list',       'Sell',      0, 1, 0, 0),
(207, '库存查询', 20, 7, 'stock',        'inventory/stock/index',       'C', 'inventory:stock:list',       'Box',       0, 1, 0, 0),
(208, '库存流水', 20, 8, 'stock-record', 'inventory/stockRecord/index', 'C', 'inventory:stockRecord:list', 'Finished',  0, 1, 0, 0),

(20101, '分类新增',   201, 1, NULL, NULL, 'F', 'inventory:category:add',    NULL, 0, 1, 0, 0),
(20102, '分类修改',   201, 2, NULL, NULL, 'F', 'inventory:category:edit',   NULL, 0, 1, 0, 0),
(20103, '分类删除',   201, 3, NULL, NULL, 'F', 'inventory:category:remove', NULL, 0, 1, 0, 0),
(20201, '商品新增',   202, 1, NULL, NULL, 'F', 'inventory:product:add',     NULL, 0, 1, 0, 0),
(20202, '商品修改',   202, 2, NULL, NULL, 'F', 'inventory:product:edit',    NULL, 0, 1, 0, 0),
(20203, '商品删除',   202, 3, NULL, NULL, 'F', 'inventory:product:remove',  NULL, 0, 1, 0, 0),
(20204, '商品导出',   202, 4, NULL, NULL, 'F', 'inventory:product:export',  NULL, 0, 1, 0, 0),
(20205, '商品导入',   202, 5, NULL, NULL, 'F', 'inventory:product:import',  NULL, 0, 1, 0, 0),
(20301, '仓库新增',   203, 1, NULL, NULL, 'F', 'inventory:warehouse:add',    NULL, 0, 1, 0, 0),
(20302, '仓库修改',   203, 2, NULL, NULL, 'F', 'inventory:warehouse:edit',   NULL, 0, 1, 0, 0),
(20303, '仓库删除',   203, 3, NULL, NULL, 'F', 'inventory:warehouse:remove', NULL, 0, 1, 0, 0),
(20401, '供应商新增', 204, 1, NULL, NULL, 'F', 'inventory:supplier:add',    NULL, 0, 1, 0, 0),
(20402, '供应商修改', 204, 2, NULL, NULL, 'F', 'inventory:supplier:edit',   NULL, 0, 1, 0, 0),
(20403, '供应商删除', 204, 3, NULL, NULL, 'F', 'inventory:supplier:remove', NULL, 0, 1, 0, 0),
(20404, '供应商导出', 204, 4, NULL, NULL, 'F', 'inventory:supplier:export', NULL, 0, 1, 0, 0),
(20501, '采购单新增', 205, 1, NULL, NULL, 'F', 'inventory:purchase:add',    NULL, 0, 1, 0, 0),
(20502, '采购单修改', 205, 2, NULL, NULL, 'F', 'inventory:purchase:edit',   NULL, 0, 1, 0, 0),
(20503, '采购单删除', 205, 3, NULL, NULL, 'F', 'inventory:purchase:remove', NULL, 0, 1, 0, 0),
(20504, '提交审批',   205, 4, NULL, NULL, 'F', 'inventory:purchase:submit', NULL, 0, 1, 0, 0),
(20505, '采购入库',   205, 5, NULL, NULL, 'F', 'inventory:purchase:inbound', NULL, 0, 1, 0, 0),
(20506, '采购单导出', 205, 6, NULL, NULL, 'F', 'inventory:purchase:export', NULL, 0, 1, 0, 0),
(20601, '销售单新增', 206, 1, NULL, NULL, 'F', 'inventory:sales:add',     NULL, 0, 1, 0, 0),
(20602, '销售单修改', 206, 2, NULL, NULL, 'F', 'inventory:sales:edit',    NULL, 0, 1, 0, 0),
(20603, '销售单删除', 206, 3, NULL, NULL, 'F', 'inventory:sales:remove',  NULL, 0, 1, 0, 0),
(20604, '提交审批',   206, 4, NULL, NULL, 'F', 'inventory:sales:submit',  NULL, 0, 1, 0, 0),
(20605, '销售出库',   206, 5, NULL, NULL, 'F', 'inventory:sales:outbound', NULL, 0, 1, 0, 0),
(20606, '销售单导出', 206, 6, NULL, NULL, 'F', 'inventory:sales:export',  NULL, 0, 1, 0, 0),
(20701, '库存调整',   207, 1, NULL, NULL, 'F', 'inventory:stock:adjust',  NULL, 0, 1, 0, 0),
(20702, '库存导出',   207, 2, NULL, NULL, 'F', 'inventory:stock:export',  NULL, 0, 1, 0, 0),
(20801, '流水导出',   208, 1, NULL, NULL, 'F', 'inventory:stockRecord:export', NULL, 0, 1, 0, 0),

-- ========== 客户关系 ==========
(401, '客户管理',   40, 1, 'customer',    'crm/customer/index',    'C', 'crm:customer:list',    'Briefcase', 0, 1, 0, 0),
(402, '联系人管理', 40, 2, 'contact',     'crm/contact/index',     'C', 'crm:contact:list',     'Phone',     0, 1, 0, 0),
(403, '商机管理',   40, 3, 'opportunity', 'crm/opportunity/index', 'C', 'crm:opportunity:list', 'TrendCharts', 0, 1, 0, 0),
(404, '跟进记录',   40, 4, 'follow',      'crm/follow/index',      'C', 'crm:follow:list',      'ChatDotRound', 0, 1, 0, 0),
(405, '合同管理',   40, 5, 'contract',    'crm/contract/index',    'C', 'crm:contract:list',    'Files',     0, 1, 0, 0),

(40101, '客户新增',   401, 1, NULL, NULL, 'F', 'crm:customer:add',      NULL, 0, 1, 0, 0),
(40102, '客户修改',   401, 2, NULL, NULL, 'F', 'crm:customer:edit',     NULL, 0, 1, 0, 0),
(40103, '客户删除',   401, 3, NULL, NULL, 'F', 'crm:customer:remove',   NULL, 0, 1, 0, 0),
(40104, '客户导出',   401, 4, NULL, NULL, 'F', 'crm:customer:export',   NULL, 0, 1, 0, 0),
(40105, '客户导入',   401, 5, NULL, NULL, 'F', 'crm:customer:import',   NULL, 0, 1, 0, 0),
(40106, '转移负责人', 401, 6, NULL, NULL, 'F', 'crm:customer:transfer', NULL, 0, 1, 0, 0),
(40201, '联系人新增', 402, 1, NULL, NULL, 'F', 'crm:contact:add',     NULL, 0, 1, 0, 0),
(40202, '联系人修改', 402, 2, NULL, NULL, 'F', 'crm:contact:edit',    NULL, 0, 1, 0, 0),
(40203, '联系人删除', 402, 3, NULL, NULL, 'F', 'crm:contact:remove',  NULL, 0, 1, 0, 0),
(40301, '商机新增',   403, 1, NULL, NULL, 'F', 'crm:opportunity:add',    NULL, 0, 1, 0, 0),
(40302, '商机修改',   403, 2, NULL, NULL, 'F', 'crm:opportunity:edit',   NULL, 0, 1, 0, 0),
(40303, '商机删除',   403, 3, NULL, NULL, 'F', 'crm:opportunity:remove', NULL, 0, 1, 0, 0),
(40304, '推进阶段',   403, 4, NULL, NULL, 'F', 'crm:opportunity:stage',  NULL, 0, 1, 0, 0),
(40305, '商机导出',   403, 5, NULL, NULL, 'F', 'crm:opportunity:export', NULL, 0, 1, 0, 0),
(40401, '跟进新增',   404, 1, NULL, NULL, 'F', 'crm:follow:add',    NULL, 0, 1, 0, 0),
(40402, '跟进修改',   404, 2, NULL, NULL, 'F', 'crm:follow:edit',   NULL, 0, 1, 0, 0),
(40403, '跟进删除',   404, 3, NULL, NULL, 'F', 'crm:follow:remove', NULL, 0, 1, 0, 0),
(40501, '合同新增',   405, 1, NULL, NULL, 'F', 'crm:contract:add',    NULL, 0, 1, 0, 0),
(40502, '合同修改',   405, 2, NULL, NULL, 'F', 'crm:contract:edit',   NULL, 0, 1, 0, 0),
(40503, '合同删除',   405, 3, NULL, NULL, 'F', 'crm:contract:remove', NULL, 0, 1, 0, 0),
(40504, '提交审批',   405, 4, NULL, NULL, 'F', 'crm:contract:submit', NULL, 0, 1, 0, 0),
(40505, '合同导出',   405, 5, NULL, NULL, 'F', 'crm:contract:export', NULL, 0, 1, 0, 0),

-- ========== 人力资源 ==========
(301, '员工档案', 30, 1, 'employee',   'hrm/employee/index',   'C', 'hrm:employee:list',   'Postcard', 0, 1, 0, 0),
(302, '考勤管理', 30, 2, 'attendance', 'hrm/attendance/index', 'C', 'hrm:attendance:list', 'Calendar', 0, 1, 0, 0),
(303, '请假管理', 30, 3, 'leave',      'hrm/leave/index',      'C', 'hrm:leave:list',      'Coffee',   0, 1, 0, 0),
(304, '薪资管理', 30, 4, 'salary',     'hrm/salary/index',     'C', 'hrm:salary:list',     'Money',    0, 1, 0, 0),

(30101, '员工新增', 301, 1, NULL, NULL, 'F', 'hrm:employee:add',    NULL, 0, 1, 0, 0),
(30102, '员工修改', 301, 2, NULL, NULL, 'F', 'hrm:employee:edit',   NULL, 0, 1, 0, 0),
(30103, '员工删除', 301, 3, NULL, NULL, 'F', 'hrm:employee:remove', NULL, 0, 1, 0, 0),
(30104, '员工导出', 301, 4, NULL, NULL, 'F', 'hrm:employee:export', NULL, 0, 1, 0, 0),
(30105, '员工导入', 301, 5, NULL, NULL, 'F', 'hrm:employee:import', NULL, 0, 1, 0, 0),
(30201, '考勤新增', 302, 1, NULL, NULL, 'F', 'hrm:attendance:add',    NULL, 0, 1, 0, 0),
(30202, '考勤修改', 302, 2, NULL, NULL, 'F', 'hrm:attendance:edit',   NULL, 0, 1, 0, 0),
(30203, '考勤删除', 302, 3, NULL, NULL, 'F', 'hrm:attendance:remove', NULL, 0, 1, 0, 0),
(30204, '考勤导出', 302, 4, NULL, NULL, 'F', 'hrm:attendance:export', NULL, 0, 1, 0, 0),
(30301, '请假新增', 303, 1, NULL, NULL, 'F', 'hrm:leave:add',    NULL, 0, 1, 0, 0),
(30302, '请假修改', 303, 2, NULL, NULL, 'F', 'hrm:leave:edit',   NULL, 0, 1, 0, 0),
(30303, '请假删除', 303, 3, NULL, NULL, 'F', 'hrm:leave:remove', NULL, 0, 1, 0, 0),
(30304, '提交审批', 303, 4, NULL, NULL, 'F', 'hrm:leave:submit', NULL, 0, 1, 0, 0),
(30401, '薪资新增', 304, 1, NULL, NULL, 'F', 'hrm:salary:add',    NULL, 0, 1, 0, 0),
(30402, '薪资修改', 304, 2, NULL, NULL, 'F', 'hrm:salary:edit',   NULL, 0, 1, 0, 0),
(30403, '薪资删除', 304, 3, NULL, NULL, 'F', 'hrm:salary:remove', NULL, 0, 1, 0, 0),
(30404, '薪资发放', 304, 4, NULL, NULL, 'F', 'hrm:salary:pay',    NULL, 0, 1, 0, 0),
(30405, '薪资导出', 304, 5, NULL, NULL, 'F', 'hrm:salary:export', NULL, 0, 1, 0, 0),

-- ========== 审批中心 ==========
(501, '我的待办', 50, 1, 'todo',    'workflow/todo/index',    'C', 'workflow:task:todo',      'Bell',      0, 1, 0, 0),
(502, '我的申请', 50, 2, 'apply',   'workflow/apply/index',   'C', 'workflow:instance:mine',  'Promotion', 0, 1, 0, 0),
(503, '流程定义', 50, 3, 'process', 'workflow/process/index', 'C', 'workflow:process:list',   'SetUp',     0, 1, 0, 0),

(50101, '审批处理', 501, 1, NULL, NULL, 'F', 'workflow:task:approve',     NULL, 0, 1, 0, 0),
(50102, '任务转办', 501, 2, NULL, NULL, 'F', 'workflow:task:transfer',    NULL, 0, 1, 0, 0),
(50201, '撤销申请', 502, 1, NULL, NULL, 'F', 'workflow:instance:cancel',  NULL, 0, 1, 0, 0),
(50301, '流程新增', 503, 1, NULL, NULL, 'F', 'workflow:process:add',      NULL, 0, 1, 0, 0),
(50302, '流程修改', 503, 2, NULL, NULL, 'F', 'workflow:process:edit',     NULL, 0, 1, 0, 0),
(50303, '流程删除', 503, 3, NULL, NULL, 'F', 'workflow:process:remove',   NULL, 0, 1, 0, 0),

-- ========== 报表中心 ==========
(601, '进销存报表', 60, 1, 'inventory', 'report/inventory/index', 'C', 'report:inventory:view', 'PieChart',  0, 1, 0, 0),
(602, '人力报表',   60, 2, 'hrm',       'report/hrm/index',       'C', 'report:hrm:view',       'Histogram', 0, 1, 0, 0),
(603, '客户报表',   60, 3, 'crm',       'report/crm/index',       'C', 'report:crm:view',       'DataLine',  0, 1, 0, 0),

(60101, '进销存报表导出', 601, 1, NULL, NULL, 'F', 'report:inventory:export', NULL, 0, 1, 0, 0),
(60201, '人力报表导出',   602, 1, NULL, NULL, 'F', 'report:hrm:export',       NULL, 0, 1, 0, 0),
(60301, '客户报表导出',   603, 1, NULL, NULL, 'F', 'report:crm:export',       NULL, 0, 1, 0, 0);

-- ---------------------------------------------------------------------------
-- 6. 角色-菜单授权
--    超级管理员用 SELECT 全量授权，避免硬编码近 200 行；其余角色按职责最小授权
-- ---------------------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;

-- 销售经理：驾驶舱 + 审批(待办/申请) + 客户关系全量 + 销售订单 + 商品/库存查看 + 客户报表
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu
WHERE id IN (1, 20, 30, 40, 50, 60, 202, 206, 207, 301, 302, 501, 502, 603, 60301, 20601, 20602, 20604, 20606, 30104)
   OR (id BETWEEN 401 AND 405)
   OR (id BETWEEN 40101 AND 40599)
   OR (id BETWEEN 50101 AND 50299);

-- 销售专员：仅本人数据，无删除/导出权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, id FROM sys_menu
WHERE id IN (1, 20, 40, 50, 206, 207, 501, 502, 20601, 20602, 20604,
             401, 402, 403, 404, 405,
             40101, 40102, 40201, 40202, 40301, 40302, 40304, 40401, 40402, 40501, 40502, 40504,
             50101, 50201);

-- 采购专员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu
WHERE id IN (1, 20, 50, 60, 202, 204, 205, 207, 208, 501, 502, 601, 60101,
             20201, 20202, 20204, 20401, 20402, 20404,
             20501, 20502, 20503, 20504, 20506, 20702, 20801, 50101, 50201);

-- 仓库管理员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, id FROM sys_menu
WHERE id IN (1, 20, 60, 201, 202, 203, 205, 206, 207, 208, 601, 60101,
             20101, 20102, 20201, 20202, 20204, 20301, 20302,
             20505, 20605, 20701, 20702, 20801);

-- 人事经理
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, id FROM sys_menu
WHERE id IN (1, 10, 30, 50, 60, 101, 104, 105, 501, 502, 602, 60201, 50101, 50201)
   OR (id BETWEEN 301 AND 304)
   OR (id BETWEEN 30101 AND 30499);

-- 财务专员（自定义数据权限示例角色）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 7, id FROM sys_menu
WHERE id IN (1, 20, 40, 60, 205, 206, 405, 601, 602, 603, 60101, 60201, 60301,
             20506, 20606, 40505, 304, 30405);

-- ---------------------------------------------------------------------------
-- 7. 数据字典：统一收口下拉枚举，避免魔法值散落在前端代码里
-- ---------------------------------------------------------------------------
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark) VALUES
(1,  '用户性别',     'sys_user_gender',       0, '用户性别列表'),
(2,  '通用状态',     'sys_normal_disable',    0, '通用启用/停用状态'),
(3,  '操作类型',     'sys_oper_type',         0, '操作日志业务类型'),
(4,  '商品状态',     'inv_product_status',    0, '商品上下架状态'),
(5,  '单据状态',     'inv_order_status',      0, '采购/销售单据状态'),
(6,  '库存业务类型', 'inv_stock_biz_type',    0, '库存流水业务类型'),
(7,  '客户来源',     'crm_customer_source',   0, '客户来源渠道'),
(8,  '客户级别',     'crm_customer_level',    0, '客户重要程度'),
(9,  '客户状态',     'crm_customer_status',   0, '客户生命周期状态'),
(10, '商机阶段',     'crm_opportunity_stage', 0, '销售漏斗阶段'),
(11, '在职状态',     'hr_employ_status',      0, '员工在职状态'),
(12, '请假类型',     'hr_leave_type',         0, '请假类型'),
(13, '考勤状态',     'hr_attendance_status',  0, '每日考勤状态'),
(14, '审批状态',     'wf_instance_status',    0, '审批流程实例状态');

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, dict_sort, list_class, is_default, status) VALUES
('sys_user_gender', '未知', '0', 1, 'info',    1, 0),
('sys_user_gender', '男',   '1', 2, 'primary', 0, 0),
('sys_user_gender', '女',   '2', 3, 'danger',  0, 0),

('sys_normal_disable', '正常', '0', 1, 'success', 1, 0),
('sys_normal_disable', '停用', '1', 2, 'danger',  0, 0),

('sys_oper_type', '其它', '0', 1, 'info',    1, 0),
('sys_oper_type', '新增', '1', 2, 'primary', 0, 0),
('sys_oper_type', '修改', '2', 3, 'warning', 0, 0),
('sys_oper_type', '删除', '3', 4, 'danger',  0, 0),
('sys_oper_type', '导出', '4', 5, 'success', 0, 0),
('sys_oper_type', '导入', '5', 6, 'success', 0, 0),
('sys_oper_type', '审批', '6', 7, 'primary', 0, 0),
('sys_oper_type', '查询', '7', 8, 'info',    0, 0),
('sys_oper_type', '授权', '8', 9, 'primary', 0, 0),
('sys_oper_type', '重置密码', '9', 10, 'warning', 0, 0),
('sys_oper_type', '清空', '10', 11, 'danger', 0, 0),

('inv_product_status', '上架', '0', 1, 'success', 1, 0),
('inv_product_status', '下架', '1', 2, 'info',    0, 0),

('inv_order_status', '草稿',   '0', 1, 'info',    1, 0),
('inv_order_status', '审批中', '1', 2, 'warning', 0, 0),
('inv_order_status', '待执行', '2', 3, 'primary', 0, 0),
('inv_order_status', '已完成', '3', 4, 'success', 0, 0),
('inv_order_status', '已驳回', '4', 5, 'danger',  0, 0),
('inv_order_status', '已取消', '5', 6, 'info',    0, 0),

('inv_stock_biz_type', '采购入库', '1', 1, 'success', 1, 0),
('inv_stock_biz_type', '销售出库', '2', 2, 'danger',  0, 0),
('inv_stock_biz_type', '盘盈',     '3', 3, 'success', 0, 0),
('inv_stock_biz_type', '盘亏',     '4', 4, 'danger',  0, 0),
('inv_stock_biz_type', '调拨入库', '5', 5, 'primary', 0, 0),
('inv_stock_biz_type', '调拨出库', '6', 6, 'warning', 0, 0),

('crm_customer_source', '网络推广',   '1', 1, 'primary', 0, 0),
('crm_customer_source', '电话营销',   '2', 2, 'warning', 0, 0),
('crm_customer_source', '客户转介绍', '3', 3, 'success', 0, 0),
('crm_customer_source', '展会',       '4', 4, 'primary', 0, 0),
('crm_customer_source', '其他',       '5', 5, 'info',    1, 0),

('crm_customer_level', '重要客户', '1', 1, 'danger',  0, 0),
('crm_customer_level', '普通客户', '2', 2, 'primary', 1, 0),
('crm_customer_level', '潜在客户', '3', 3, 'info',    0, 0),

('crm_customer_status', '潜在',   '1', 1, 'info',    1, 0),
('crm_customer_status', '跟进中', '2', 2, 'warning', 0, 0),
('crm_customer_status', '已成交', '3', 3, 'success', 0, 0),
('crm_customer_status', '已流失', '4', 4, 'danger',  0, 0),

('crm_opportunity_stage', '初步接洽', '1', 1, 'info',    1, 0),
('crm_opportunity_stage', '需求确认', '2', 2, 'primary', 0, 0),
('crm_opportunity_stage', '方案报价', '3', 3, 'primary', 0, 0),
('crm_opportunity_stage', '商务谈判', '4', 4, 'warning', 0, 0),
('crm_opportunity_stage', '赢单',     '5', 5, 'success', 0, 0),
('crm_opportunity_stage', '输单',     '6', 6, 'danger',  0, 0),

('hr_employ_status', '正式', '1', 1, 'success', 1, 0),
('hr_employ_status', '试用', '2', 2, 'warning', 0, 0),
('hr_employ_status', '离职', '3', 3, 'info',    0, 0),

('hr_leave_type', '事假', '1', 1, 'info',    1, 0),
('hr_leave_type', '病假', '2', 2, 'warning', 0, 0),
('hr_leave_type', '年假', '3', 3, 'success', 0, 0),
('hr_leave_type', '调休', '4', 4, 'primary', 0, 0),
('hr_leave_type', '婚假', '5', 5, 'danger',  0, 0),
('hr_leave_type', '产假', '6', 6, 'danger',  0, 0),
('hr_leave_type', '丧假', '7', 7, 'info',    0, 0),

('hr_attendance_status', '正常', '1', 1, 'success', 1, 0),
('hr_attendance_status', '迟到', '2', 2, 'warning', 0, 0),
('hr_attendance_status', '早退', '3', 3, 'warning', 0, 0),
('hr_attendance_status', '缺卡', '4', 4, 'danger',  0, 0),
('hr_attendance_status', '旷工', '5', 5, 'danger',  0, 0),
('hr_attendance_status', '请假', '6', 6, 'info',    0, 0),
('hr_attendance_status', '出差', '7', 7, 'primary', 0, 0),

('wf_instance_status', '审批中', '0', 1, 'warning', 1, 0),
('wf_instance_status', '已通过', '1', 2, 'success', 0, 0),
('wf_instance_status', '已驳回', '2', 3, 'danger',  0, 0),
('wf_instance_status', '已撤销', '3', 4, 'info',    0, 0);

-- ---------------------------------------------------------------------------
-- 8. 审批流程定义
--    condition_expr 为 SpEL 表达式，对流程实例的 form_data（JSON）求值：
--      #amount     采购/销售/合同金额
--      #leaveDays  请假天数
--    表达式为空表示该节点无条件进入
-- ---------------------------------------------------------------------------
INSERT INTO wf_process (id, process_key, process_name, business_type, category, form_route, version, status, remark) VALUES
(1, 'PURCHASE_ORDER', '采购订单审批', 'PURCHASE_ORDER', '采购管理', '/inventory/purchase', 1, 0, '采购单提交后由部门负责人审批，金额超过 1 万元需总经理复审'),
(2, 'SALES_ORDER',    '销售订单审批', 'SALES_ORDER',    '销售管理', '/inventory/sales',    1, 0, '销售单提交后由销售经理审批，金额超过 5 万元需总经理复审'),
(3, 'HR_LEAVE',       '员工请假审批', 'HR_LEAVE',       '人事管理', '/hrm/leave',          1, 0, '请假单由部门负责人审批，超过 3 天需人事经理复审'),
(4, 'CRM_CONTRACT',   '销售合同审批', 'CRM_CONTRACT',   '合同管理', '/crm/contract',       1, 0, '合同由销售经理审批后，需财务与人事双人会签（演示 ALL 会签模式）');

INSERT INTO wf_node (id, process_id, node_key, node_name, node_order, approver_type, approver_value, approve_mode, condition_expr, remark) VALUES
-- 采购订单审批
(1, 1, 'DEPT_LEADER', '部门负责人审批', 1, 'DEPT_LEADER', NULL,          'ANY', NULL,               '自动解析发起人所在部门的负责人'),
(2, 1, 'GM_AUDIT',    '总经理审批',     2, 'ROLE',        'SUPER_ADMIN', 'ANY', '#amount > 10000',  '金额大于 1 万元才进入该节点'),
-- 销售订单审批
(3, 2, 'DEPT_LEADER', '销售经理审批',   1, 'DEPT_LEADER', NULL,          'ANY', NULL,               '自动解析发起人所在部门的负责人'),
(4, 2, 'GM_AUDIT',    '总经理审批',     2, 'ROLE',        'SUPER_ADMIN', 'ANY', '#amount > 50000',  '金额大于 5 万元才进入该节点'),
-- 请假审批
(5, 3, 'DEPT_LEADER', '部门负责人审批', 1, 'DEPT_LEADER', NULL,          'ANY', NULL,               '自动解析发起人所在部门的负责人'),
(6, 3, 'HR_AUDIT',    '人事经理审批',   2, 'ROLE',        'HR_MANAGER',  'ANY', '#leaveDays > 3',   '请假超过 3 天才进入该节点'),
-- 合同审批
(7, 4, 'DEPT_LEADER', '销售经理审批',   1, 'DEPT_LEADER', NULL,          'ANY', NULL,               '自动解析发起人所在部门的负责人'),
(8, 4, 'JOINT_AUDIT', '财务人事会签',   2, 'ROLE',        'FINANCE_STAFF,HR_MANAGER', 'ALL', NULL,  'ALL 会签：所有审批人全部通过才流转');
