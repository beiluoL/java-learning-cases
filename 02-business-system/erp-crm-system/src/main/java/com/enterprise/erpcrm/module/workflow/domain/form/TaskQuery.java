package com.enterprise.erpcrm.module.workflow.domain.form;

import com.enterprise.erpcrm.common.base.BasePageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 我的待办分页查询条件。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class TaskQuery extends BasePageQuery {

    /** 节点名称（模糊） */
    private String nodeName;
    /** 流程标识 */
    private String processKey;
}
