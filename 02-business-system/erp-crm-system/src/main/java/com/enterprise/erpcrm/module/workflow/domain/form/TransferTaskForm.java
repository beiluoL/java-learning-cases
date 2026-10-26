package com.enterprise.erpcrm.module.workflow.domain.form;

import lombok.Getter;
import lombok.Setter;

/**
 * 审批任务转办表单。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class TransferTaskForm {

    /** 待转办的审批任务 id */
    private Long taskId;
    /** 转办目标用户 id */
    private Long targetUserId;
}
