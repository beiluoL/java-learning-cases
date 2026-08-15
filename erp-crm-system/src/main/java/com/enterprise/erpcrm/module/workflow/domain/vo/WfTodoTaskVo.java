package com.enterprise.erpcrm.module.workflow.domain.vo;

import com.enterprise.erpcrm.module.workflow.entity.WfInstance;
import com.enterprise.erpcrm.module.workflow.entity.WfTask;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * 我的待办 VO（任务 + 所属实例），便于前端展示单据标题。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class WfTodoTaskVo implements Serializable {

    private WfTask task;
    private WfInstance instance;
}
