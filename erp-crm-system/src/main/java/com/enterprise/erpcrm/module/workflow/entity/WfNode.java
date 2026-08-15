package com.enterprise.erpcrm.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.enterprise.erpcrm.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程节点定义（wf_node）。
 * <ul>
 *   <li>{@code approverType}：USER / ROLE / DEPT_LEADER / INITIATOR_LEADER</li>
 *   <li>{@code approverValue}：USER 为用户 id 列表，ROLE 为角色标识列表，逗号分隔</li>
 *   <li>{@code approveMode}：ALL 会签（全部通过才流转）/ ANY 或签（任一通过即流转）</li>
 *   <li>{@code conditionExpr}：SpEL 条件，对流程实例 form_data 求值，为空表示无条件进入</li>
 * </ul>
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("wf_node")
public class WfNode extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属流程定义 id */
    private Long processId;
    /** 节点标识 */
    private String nodeKey;
    /** 节点名称 */
    private String nodeName;
    /** 节点顺序，从 1 开始升序流转 */
    private Integer nodeOrder;
    /** 审批人类型：USER/ROLE/DEPT_LEADER/INITIATOR_LEADER */
    private String approverType;
    /** 审批人取值（逗号分隔） */
    private String approverValue;
    /** 多人审批策略：ALL 会签 / ANY 或签 */
    private String approveMode;
    /** SpEL 条件表达式，如 #amount > 10000 */
    private String conditionExpr;
    /** 备注 */
    private String remark;
}
