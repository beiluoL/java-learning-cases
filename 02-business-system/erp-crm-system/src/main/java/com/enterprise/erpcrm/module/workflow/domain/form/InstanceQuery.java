package com.enterprise.erpcrm.module.workflow.domain.form;

import com.enterprise.erpcrm.common.base.BasePageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 我的流程实例分页查询条件。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class InstanceQuery extends BasePageQuery {

    /** 标题（模糊） */
    private String title;
    /** 状态：0 审批中 1 已通过 2 已驳回 3 已撤销 */
    private Integer status;
    /** 流程标识 */
    private String processKey;
}
