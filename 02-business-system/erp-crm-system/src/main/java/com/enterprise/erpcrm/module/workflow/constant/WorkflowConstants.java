package com.enterprise.erpcrm.module.workflow.constant;

/**
 * 审批流模块常量集合（状态、审批人类型、会签模式、操作类型）。
 * <p>与数据库 wf_* 表字段取值、前端字典保持一致，避免魔法值散落。</p>
 *
 * @author erp-crm-system
 */
public final class WorkflowConstants {

    private WorkflowConstants() {
    }

    /** 流程定义状态：0 启用 / 1 停用 */
    public static final int PROCESS_ENABLED = 0;
    public static final int PROCESS_DISABLED = 1;

    /** 流程实例状态：0 审批中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    public static final int INSTANCE_RUNNING = 0;
    public static final int INSTANCE_APPROVED = 1;
    public static final int INSTANCE_REJECTED = 2;
    public static final int INSTANCE_CANCELLED = 3;

    /** 审批任务状态：0 待处理 / 1 已通过 / 2 已驳回 / 3 已转办 / 4 已失效 */
    public static final int TASK_PENDING = 0;
    public static final int TASK_APPROVED = 1;
    public static final int TASK_REJECTED = 2;
    public static final int TASK_TRANSFERRED = 3;
    public static final int TASK_INVALID = 4;

    /** 审批人类型 */
    public static final String APPROVER_USER = "USER";
    public static final String APPROVER_ROLE = "ROLE";
    public static final String APPROVER_DEPT_LEADER = "DEPT_LEADER";
    public static final String APPROVER_INITIATOR_LEADER = "INITIATOR_LEADER";

    /** 多人审批策略：ALL 会签 / ANY 或签 */
    public static final String MODE_ALL = "ALL";
    public static final String MODE_ANY = "ANY";

    /** 操作类型 */
    public static final String OP_SUBMIT = "SUBMIT";
    public static final String OP_APPROVE = "APPROVE";
    public static final String OP_REJECT = "REJECT";
    public static final String OP_CANCEL = "CANCEL";
    public static final String OP_TRANSFER = "TRANSFER";
    public static final String OP_AUTO_PASS = "AUTO_PASS";
    /** 流程正常走完所有节点、最终审批通过（区别于 AUTO_PASS 的单节点自动跳过） */
    public static final String OP_FINISH = "FINISH";
}
