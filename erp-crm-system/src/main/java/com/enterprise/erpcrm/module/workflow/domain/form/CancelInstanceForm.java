package com.enterprise.erpcrm.module.workflow.domain.form;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤销流程表单。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class CancelInstanceForm {

    /** 流程实例 id */
    private Long instanceId;
}
