package com.enterprise.erpcrm.module.workflow.service;

import com.enterprise.erpcrm.module.workflow.domain.form.CancelInstanceForm;
import com.enterprise.erpcrm.module.workflow.domain.form.StartInstanceForm;
import com.enterprise.erpcrm.module.workflow.domain.form.TransferTaskForm;
import com.enterprise.erpcrm.module.workflow.entity.WfNode;
import com.enterprise.erpcrm.module.workflow.entity.WfProcess;

/**
 * 审批流引擎服务：流程定义保存、发起实例、审批（通过/驳回）、撤销、转办。
 * <p>核心流转规则见 {@code WorkflowEngineServiceImpl}：按节点顺序逐节点激活，
 * 支持条件进入（SpEL）、或签/会签、审批人为空自动通过（可配置）。</p>
 *
 * @author erp-crm-system
 */
public interface WorkflowEngineService {

    /**
     * 发起流程实例。发起人姓名与部门由引擎依据 {@code initiatorId} 从用户表解析，
     * 避免依赖 JWT 中未必包含的字段。
     *
     * @param form        发起表单（流程标识、标题、业务单据、表单快照）
     * @param initiatorId 发起人 id
     * @return 流程实例 id
     */
    Long startInstance(StartInstanceForm form, Long initiatorId);

    /**
     * 审批任务：通过或驳回。
     *
     * @param taskId    任务 id
     * @param operatorId 操作人 id
     * @param agree     是否通过
     * @param comment   审批意见
     */
    void completeTask(Long taskId, Long operatorId, boolean agree, String comment);

    /**
     * 撤销流程实例（仅发起人/超级管理员可操作，且流程须在审批中）。
     *
     * @param instanceId 流程实例 id
     * @param operatorId 操作人 id
     */
    void cancelInstance(Long instanceId, Long operatorId);

    /**
     * 转办审批任务给指定用户。
     *
     * @param taskId       任务 id
     * @param operatorId   操作人 id（须为当前审批人或超级管理员）
     * @param targetUserId 目标用户 id
     */
    void transferTask(Long taskId, Long operatorId, Long targetUserId);

    /**
     * 保存流程定义（新增或更新）。更新时会先删除旧节点再写入新节点。
     *
     * @param process 流程定义（含 nodes 节点列表）
     * @return 流程定义 id
     */
    Long saveProcess(WfProcess process);

    /**
     * 删除流程定义（逻辑删除）及其节点。
     *
     * @param id 流程定义 id
     */
    void removeProcess(Long id);
}
