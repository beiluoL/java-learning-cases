package com.enterprise.erpcrm.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批流水（wf_record）。
 * <p>记录流程实例全生命周期的每一次操作（提交/通过/驳回/撤销/转办/条件自动跳过），
 * 供审批轨迹追溯。该表无逻辑删除字段，故不继承 {@code BaseEntity}。</p>
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("wf_record")
public class WfRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 流程实例 id */
    private Long instanceId;
    /** 节点定义 id */
    private Long nodeId;
    /** 节点名称 */
    private String nodeName;
    /** 操作类型：SUBMIT/APPROVE/REJECT/CANCEL/TRANSFER/AUTO_PASS */
    private String operation;
    /** 操作人用户 id */
    private Long operatorId;
    /** 操作人姓名 */
    private String operatorName;
    /** 审批意见 */
    private String approveComment;
    /** 操作时间 */
    private LocalDateTime operTime;
}
