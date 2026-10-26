package com.enterprise.erpcrm.module.workflow.domain.form;

import lombok.Getter;
import lombok.Setter;

/**
 * 发起流程表单。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class StartInstanceForm {

    /** 流程标识，对应 wf_process.process_key */
    private String processKey;
    /** 审批标题 */
    private String title;
    /** 业务单据 id（可选） */
    private Long businessId;
    /** 业务单据编号（可选） */
    private String businessNo;
    /** 业务表单快照 JSON（供条件表达式求值与审批页展示） */
    private String formData;
}
