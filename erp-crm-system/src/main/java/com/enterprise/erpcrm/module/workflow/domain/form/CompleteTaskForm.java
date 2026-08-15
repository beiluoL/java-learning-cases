package com.enterprise.erpcrm.module.workflow.domain.form;

import lombok.Getter;
import lombok.Setter;

/**
 * 审批（通过/驳回）表单。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class CompleteTaskForm {

    /** 审批任务 id */
    private Long taskId;
    /** 是否通过，默认 true（通过） */
    private Boolean agree = true;
    /** 审批意见 */
    private String comment;
}
