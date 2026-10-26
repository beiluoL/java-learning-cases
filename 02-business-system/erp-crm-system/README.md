# ERP-CRM 综合管理系统

企业级 **ERP + CRM** 综合管理系统（后端骨架）。基于 Spring Boot 3 + MyBatis-Plus 构建，目标是覆盖 RBAC 权限、审批流引擎、报表、导入导出等核心能力。

> 当前状态：**后端纵向切片先行**。RBAC 与审批流引擎已完成并可端到端联调；进销存 / HRM / CRM / 报表模块 SQL 已就绪，Java 层待实现（见文末路线图）。

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 / JDK | Java 17 |
| 框架 | Spring Boot 3.4.1 |
| 持久层 | MyBatis-Plus 3.5.5 |
| 数据库 | H2（内存，MySQL 兼容模式，开箱即跑）/ MySQL（生产） |
| 安全 | JWT + RBAC 切面（`@RequiresPermissions`） |
| 构建 | Maven |

## 快速开始

```bash
# 默认 H2 内存库，无需安装数据库，克隆即跑
cd erp-crm-system
unset SERVER__PORT
mvn -B spring-boot:run -Dspring-boot.run.arguments="--server.port=8090 --spring.profiles.active=h2"
```

启动后：

- 接口基址：`http://localhost:8090`
- H2 控制台：`http://localhost:8090/h2-console`（JDBC `jdbc:h2:mem:erpcrm`）
- 默认管理员：`admin` / `123456`

切换到 MySQL：将 `spring.profiles.active` 改为 `mysql`，并按 `src/main/resources/db/mysql/schema.sql` + `data.sql` 初始化库表。

## 模块进度

| 模块 | 包 | 状态 |
|---|---|---|
| 系统 / RBAC | `module.system` | ✅ 用户 / 角色 / 部门 / 菜单 / 权限切面 |
| 审批流引擎 | `module.workflow` | ✅ 流程定义 / 实例 / 任务 / 流水 + 引擎 |
| 进销存 | `module.inventory` | ⏳ SQL 已建表，Java 待实现 |
| HRM | `module.hrm` | ⏳ SQL 已建表，Java 待实现 |
| CRM | `module.crm` | ⏳ SQL 已建表，Java 待实现 |
| 报表 | `module.report` | ⏳ SQL 已建表，Java 待实现 |

## 审批流引擎设计

数据模型（`wf_*` 表）：

- `wf_process` 流程定义（含 `process_key`、`business_type`、启用状态）
- `wf_node` 节点（有序 `node_order`、审批人策略、会签/或签、SpEL 条件）
- `wf_instance` 流程实例（状态：审批中/已通过/已驳回/已撤销）
- `wf_task` 审批任务（待办/通过/驳回/转办/失效）
- `wf_record` 审批流水（操作轨迹，无逻辑删除列）

**审批人策略（`approver_type`）**

| 策略 | 说明 |
|---|---|
| `USER` | 指定用户 id 列表（`approver_value` 逗号分隔） |
| `ROLE` | 按角色 key 解析出该角色下所有用户 |
| `DEPT_LEADER` | 自动解析**发起人所在部门**的负责人（`sys_dept.leader_user_id`） |
| `INITIATOR_LEADER` | 同 DEPT_LEADER（语义别名） |

> ⚠️ 实现要点：`DEPT_LEADER` / `INITIATOR_LEADER` **不依赖 `approver_value`**（种子数据中恒为 `NULL`）。解析器只对 `USER` / `ROLE` 校验 `value` 非空，否则所有部门负责人节点会被误判为「审批人为空 → 自动通过」。

**流转规则**

- 节点条件：`condition_expr` 为 SpEL 表达式，对 `form_data` JSON 求值；缺变量或异常视为不成立 → 该节点 `AUTO_PASS` 跳过。
- 审批模式：`ANY`（或签，任一通过即推进）/ `ALL`（会签，全部通过才推进）。
- 驳回：整单置为「已驳回」，待办任务失效，触发 `ProcessCallback.onRejected`（业务模块 SPI 接入）。
- 完成：无更多节点时实例置「已通过」，写入 `FINISH` 流水并触发 `onApproved`。

**业务接入（SPI）**：实现 `WorkflowEngineService` 包下的 `ProcessCallback` 接口，`businessType()` 返回对应业务类型，由 `ProcessCallbackDispatcher` 按类型路由；无实现时静默跳过。

## 审批流接口（`/api/workflow`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/process/list` | 流程定义分页 |
| GET | `/process/{id}` | 流程定义 + 节点详情 |
| POST / PUT | `/process` | 新增 / 编辑流程定义 |
| DELETE | `/process/{ids}` | 删除流程定义 |
| POST | `/instance/start` | 发起流程实例 |
| GET | `/instance/mine` | 我发起的实例 |
| GET | `/instance/{id}` | 实例详情（节点 + 任务 + 流水） |
| POST | `/instance/cancel` | 撤销实例 |
| GET | `/task/todo` | 我的待办 |
| POST | `/task/complete` | 通过 / 驳回任务 |
| POST | `/task/transfer` | 转办 |

所有接口受 `@RequiresPermissions` 保护，鉴权失败返回业务码 401（HTTP 200 + `Result`，非 HTTP 401）。

## 端到端验证

`scripts/wf_e2e_test.py` 以 admin 登录，发起 `HR_LEAVE`（请假 5 天）流程，自动完成所有待办直至「已通过」：

```bash
python3 scripts/wf_e2e_test.py
# 覆盖：流程列表 → 请假流程详情 → 发起 → 部门负责人审批 → 人事经理审批(条件 #leaveDays>3) → 通过
```

已验证分支：

- ✅ 两级人工审批 happy path（状态 `1 已通过`）
- ✅ 驳回路径（状态 `2 已驳回`）
- ✅ 条件跳过（请假 ≤3 天，`HR_AUDIT` 节点 `AUTO_PASS` 跳过，流程正常结束）

## 路线图

1. 进销存闭环：库存（乐观锁 + 出入库流水）、采购 / 销售订单对接审批流。
2. HRM / CRM 业务模块 Java 层实现。
3. 报表模块（统计 + 导出）。
4. Excel 导入导出通用能力。
5. Vue3 前端（Element Plus + 动态路由 + 权限指令）。
