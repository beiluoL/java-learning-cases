package com.enterprise.erpcrm.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.enterprise.erpcrm.common.base.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批任务（wf_task）。
 * <p>每激活一个节点，引擎按审批人解析结果生成一条或多条任务（会签时一人一条）。
 * 节点策略决定「或签（任一通过）」或「会签（全部通过）」后流转到下一节点。</p>
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("wf_task")
public class WfTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程实例 id */
    private Long instanceId;
    /** 节点定义 id */
    private Long nodeId;
    /** 节点标识（冗余） */
    private String nodeKey;
    /** 节点名称（冗余） */
    private String nodeName;
    /** 节点顺序（冗余） */
    private Integer nodeOrder;
    /** 多人审批策略（冗余）：ALL / ANY */
    private String approveMode;
    /** 审批人用户 id */
    private Long approverId;
    /** 审批人姓名 */
    private String approverName;
    /** 状态：0 待处理 1 已通过 2 已驳回 3 已转办 4 已失效 */
    private Integer status;
    /** 审批意见 */
    private String approveComment;
    /** 审批时间 */
    private LocalDateTime approveTime;
}
