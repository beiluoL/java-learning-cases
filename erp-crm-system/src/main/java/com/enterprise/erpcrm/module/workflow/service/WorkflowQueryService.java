package com.enterprise.erpcrm.module.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.enterprise.erpcrm.module.workflow.domain.form.InstanceQuery;
import com.enterprise.erpcrm.module.workflow.domain.form.ProcessQuery;
import com.enterprise.erpcrm.module.workflow.domain.form.TaskQuery;
import com.enterprise.erpcrm.module.workflow.domain.vo.WfInstanceDetailVo;
import com.enterprise.erpcrm.module.workflow.domain.vo.WfProcessVo;
import com.enterprise.erpcrm.module.workflow.domain.vo.WfTodoTaskVo;
import com.enterprise.erpcrm.module.workflow.entity.WfInstance;
import com.enterprise.erpcrm.module.workflow.entity.WfProcess;

/**
 * 审批流查询服务：流程定义、我的实例、我的待办、实例详情。
 *
 * @author erp-crm-system
 */
public interface WorkflowQueryService {

    /** 流程定义分页列表 */
    IPage<WfProcess> pageProcess(ProcessQuery query);

    /** 流程定义详情（含有序节点） */
    WfProcessVo getProcess(Long id);

    /** 我发起的流程实例分页列表 */
    IPage<WfInstance> pageMyInstance(InstanceQuery query, Long userId);

    /** 我的待办分页列表（任务 + 所属实例） */
    IPage<WfTodoTaskVo> pageMyTodo(TaskQuery query, Long userId);

    /** 流程实例详情（实例 + 节点 + 任务 + 审批流水） */
    WfInstanceDetailVo detail(Long instanceId);
}
