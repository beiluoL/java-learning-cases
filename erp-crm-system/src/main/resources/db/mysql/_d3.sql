
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
