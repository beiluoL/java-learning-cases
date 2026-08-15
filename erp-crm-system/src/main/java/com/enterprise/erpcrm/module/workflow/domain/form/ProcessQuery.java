package com.enterprise.erpcrm.module.workflow.domain.form;

import com.enterprise.erpcrm.common.base.BasePageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程定义分页查询条件。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class ProcessQuery extends BasePageQuery {

    /** 流程标识（精确） */
    private String processKey;
    /** 流程名称（模糊） */
    private String processName;
    /** 状态：0 启用 1 停用 */
    private Integer status;
    /** 分类 */
    private String category;
}
