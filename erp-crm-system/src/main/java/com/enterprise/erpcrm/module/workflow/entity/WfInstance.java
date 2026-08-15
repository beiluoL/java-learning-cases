package com.enterprise.erpcrm.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.enterprise.erpcrm.common.base.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程实例（wf_instance）。
 * <p>发起人提交后由引擎按节点定义逐节点生成审批任务（{@link WfTask}），
 * 全部节点通过则状态变为「已通过」，任一点驳回/撤销则终止。</p>
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("wf_instance")
public class WfInstance extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程实例编号（唯一） */
    private String instanceNo;
    /** 流程定义 id */
    private Long processId;
    /** 流程标识（冗余） */
    private String processKey;
    /** 流程名称（冗余） */
    private String processName;
    /** 业务类型 */
    private String businessType;
    /** 业务单据 id */
    private Long businessId;
    /** 业务单据编号 */
    private String businessNo;
    /** 审批标题 */
    private String title;
    /** 业务表单快照（JSON），供条件表达式求值与审批页展示 */
    private String formData;
    /** 发起人用户 id */
    private Long initiatorId;
    /** 发起人姓名 */
    private String initiatorName;
    /** 发起人部门 id（DEPT_LEADER 策略依赖） */
    private Long initiatorDeptId;
    /** 当前节点 id */
    private Long currentNodeId;
    /** 当前节点名称 */
    private String currentNodeName;
    /** 状态：0 审批中 1 已通过 2 已驳回 3 已撤销 */
    private Integer status;
    /** 提交时间 */
    private LocalDateTime submitTime;
    /** 结束时间 */
    private LocalDateTime endTime;
}
