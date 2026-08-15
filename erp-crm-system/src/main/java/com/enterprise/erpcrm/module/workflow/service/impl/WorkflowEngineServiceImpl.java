package com.enterprise.erpcrm.module.workflow.service.impl;

import com.enterprise.erpcrm.common.constant.ErpConstants;
import com.enterprise.erpcrm.common.exception.BizException;
import com.enterprise.erpcrm.common.result.RCode;
import com.enterprise.erpcrm.module.system.entity.SysDept;
import com.enterprise.erpcrm.module.system.entity.SysRole;
import com.enterprise.erpcrm.module.system.entity.SysUser;
import com.enterprise.erpcrm.module.system.entity.SysUserRole;
import com.enterprise.erpcrm.module.system.mapper.SysDeptMapper;
import com.enterprise.erpcrm.module.system.mapper.SysRoleMapper;
import com.enterprise.erpcrm.module.system.mapper.SysUserMapper;
import com.enterprise.erpcrm.module.system.mapper.SysUserRoleMapper;
import com.enterprise.erpcrm.module.workflow.constant.WorkflowConstants;
import com.enterprise.erpcrm.module.workflow.domain.form.StartInstanceForm;
import com.enterprise.erpcrm.module.workflow.entity.WfInstance;
import com.enterprise.erpcrm.module.workflow.entity.WfNode;
import com.enterprise.erpcrm.module.workflow.entity.WfProcess;
import com.enterprise.erpcrm.module.workflow.entity.WfRecord;
import com.enterprise.erpcrm.module.workflow.entity.WfTask;
import com.enterprise.erpcrm.module.workflow.mapper.WfInstanceMapper;
import com.enterprise.erpcrm.module.workflow.mapper.WfNodeMapper;
import com.enterprise.erpcrm.module.workflow.mapper.WfProcessMapper;
import com.enterprise.erpcrm.module.workflow.mapper.WfRecordMapper;
import com.enterprise.erpcrm.module.workflow.mapper.WfTaskMapper;
import com.enterprise.erpcrm.module.workflow.service.WorkflowEngineService;
import com.enterprise.erpcrm.module.workflow.spi.ProcessCallbackDispatcher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 审批流引擎实现。
 * <p>流转模型：流程定义包含按 {@code nodeOrder} 升序排列的审批节点；
 * 发起后引擎从首节点开始逐节点激活，支持：</p>
 * <ul>
 *   <li><b>条件进入</b>：节点 {@code conditionExpr} 为 SpEL，对实例 form_data 求值，不成立则自动跳过；</li>
 *   <li><b>审批人解析</b>：USER（指定用户）/ ROLE（角色内用户）/ DEPT_LEADER（发起人部门负责人）；</li>
 *   <li><b>会签/或签</b>：ALL 需全部通过，ANY 任一通过即流转；</li>
 *   <li><b>审批人为空</b>：按 {@code erp.workflow.auto-pass-on-empty-approver} 决定自动通过或停留等待配置。</li>
 * </ul>
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class WorkflowEngineServiceImpl implements WorkflowEngineService {

    private final WfProcessMapper processMapper;
    private final WfNodeMapper nodeMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final WfRecordMapper recordMapper;

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final ProcessCallbackDispatcher callbackDispatcher;
    private final ObjectMapper objectMapper;

    @Value("${erp.workflow.auto-pass-on-empty-approver:true}")
    private boolean autoPassOnEmpty;

    private static final ExpressionParser SPEL = new SpelExpressionParser();

    // ============================ 发起流程 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long startInstance(StartInstanceForm form, Long initiatorId) {
        if (form.getProcessKey() == null || form.getProcessKey().isBlank()) {
            throw new BizException(RCode.VALID_ERROR, "流程标识不能为空");
        }
        if (form.getTitle() == null || form.getTitle().isBlank()) {
            throw new BizException(RCode.VALID_ERROR, "审批标题不能为空");
        }
        SysUser initiator = sysUserMapper.selectById(initiatorId);
        if (initiator == null) {
            throw new BizException(RCode.NOT_FOUND, "发起人不存在");
        }
        String initiatorName = initiator.getRealName();
        Long initiatorDeptId = initiator.getDeptId();
        WfProcess process = processMapper.selectOne(new LambdaQueryWrapper<WfProcess>()
                .eq(WfProcess::getProcessKey, form.getProcessKey())
                .eq(WfProcess::getStatus, WorkflowConstants.PROCESS_ENABLED));
        if (process == null) {
            throw new BizException(RCode.NOT_FOUND, "流程定义不存在或已停用：" + form.getProcessKey());
        }
        List<WfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
                .eq(WfNode::getProcessId, process.getId()).orderByAsc(WfNode::getNodeOrder));
        if (nodes.isEmpty()) {
            throw new BizException(RCode.BIZ_ERROR, "流程定义未配置审批节点：" + form.getProcessKey());
        }

        WfInstance instance = new WfInstance();
        instance.setInstanceNo("WF" + System.currentTimeMillis());
        instance.setProcessId(process.getId());
        instance.setProcessKey(process.getProcessKey());
        instance.setProcessName(process.getProcessName());
        instance.setBusinessType(process.getBusinessType());
        instance.setBusinessId(form.getBusinessId());
        instance.setBusinessNo(form.getBusinessNo());
        instance.setTitle(form.getTitle());
        instance.setFormData(form.getFormData());
        instance.setInitiatorId(initiatorId);
        instance.setInitiatorName(initiatorName);
        instance.setInitiatorDeptId(initiatorDeptId);
        instance.setStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setSubmitTime(LocalDateTime.now());
        instanceMapper.insert(instance);

        writeRecord(instance.getId(), null, null, initiatorId, initiatorName,
                WorkflowConstants.OP_SUBMIT, "发起申请");

        activateFrom(instance, 0, nodes);
        return instance.getId();
    }

    /**
     * 从 {@code fromOrder} 之后的节点中，寻找第一个满足条件的节点并激活；
     * 若全部跳过/条件不成立，则流程直接通过。
     */
    private void activateFrom(WfInstance instance, int fromOrder, List<WfNode> nodes) {
        for (WfNode node : nodes) {
            if (node.getNodeOrder() == null || node.getNodeOrder() <= fromOrder) {
                continue;
            }
            if (!evalCondition(node.getConditionExpr(), instance.getFormData())) {
                writeRecord(instance.getId(), node.getId(), node.getNodeName(),
                        instance.getInitiatorId(), instance.getInitiatorName(),
                        WorkflowConstants.OP_AUTO_PASS, "条件不满足，自动跳过");
                continue;
            }
            List<Long> approvers = resolveApprovers(node, instance.getInitiatorDeptId());
            if (approvers.isEmpty()) {
                if (autoPassOnEmpty) {
                    writeRecord(instance.getId(), node.getId(), node.getNodeName(),
                            instance.getInitiatorId(), instance.getInitiatorName(),
                            WorkflowConstants.OP_AUTO_PASS, "审批人为空，系统自动通过");
                    continue;
                }
                // 审批人为空且不允许自动通过：停留在该节点等待配置
                instance.setCurrentNodeId(node.getId());
                instance.setCurrentNodeName(node.getNodeName());
                instanceMapper.updateById(instance);
                return;
            }
            Map<Long, String> names = loadNames(approvers);
            for (Long uid : approvers) {
                WfTask task = new WfTask();
                task.setInstanceId(instance.getId());
                task.setNodeId(node.getId());
                task.setNodeKey(node.getNodeKey());
                task.setNodeName(node.getNodeName());
                task.setNodeOrder(node.getNodeOrder());
                task.setApproveMode(node.getApproveMode());
                task.setApproverId(uid);
                task.setApproverName(names.getOrDefault(uid, "未知"));
                task.setStatus(WorkflowConstants.TASK_PENDING);
                taskMapper.insert(task);
            }
            instance.setCurrentNodeId(node.getId());
            instance.setCurrentNodeName(node.getNodeName());
            instanceMapper.updateById(instance);
            return;
        }
        // 没有更多节点 —— 流程直接通过
        finishApproved(instance);
    }

    // ============================ 审批 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Long taskId, Long operatorId, boolean agree, String comment) {
        WfTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(RCode.NOT_FOUND, "审批任务不存在");
        }
        if (task.getStatus() != WorkflowConstants.TASK_PENDING) {
            throw new BizException(RCode.WORKFLOW_ALREADY_DONE, "该任务已处理，不可重复操作");
        }
        if (!operatorId.equals(task.getApproverId()) && !isSuperAdmin(operatorId)) {
            throw new BizException(RCode.FORBIDDEN, "无权处理该审批任务");
        }
        WfInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new BizException(RCode.NOT_FOUND, "流程实例不存在");
        }
        if (instance.getStatus() != WorkflowConstants.INSTANCE_RUNNING) {
            throw new BizException(RCode.WORKFLOW_ALREADY_DONE, "流程已结束，不可操作");
        }
        if (!task.getNodeId().equals(instance.getCurrentNodeId())) {
            throw new BizException(RCode.WORKFLOW_NODE_MISMATCH, "当前审批节点与任务不匹配");
        }

        task.setStatus(agree ? WorkflowConstants.TASK_APPROVED : WorkflowConstants.TASK_REJECTED);
        task.setApproveComment(comment);
        task.setApproveTime(LocalDateTime.now());
        taskMapper.updateById(task);
        writeRecord(instance.getId(), task.getNodeId(), task.getNodeName(), operatorId,
                loadName(operatorId), agree ? WorkflowConstants.OP_APPROVE : WorkflowConstants.OP_REJECT, comment);

        // 驳回：整单驳回
        if (!agree) {
            instance.setStatus(WorkflowConstants.INSTANCE_REJECTED);
            instance.setEndTime(LocalDateTime.now());
            instanceMapper.updateById(instance);
            invalidPendingTasks(instance.getId(), task.getId());
            callbackDispatcher.onRejected(instance);
            return;
        }

        // 通过：判断当前节点是否满足会签/或签完成条件
        WfNode node = nodeMapper.selectById(task.getNodeId());
        if (!isNodeComplete(instance.getId(), node)) {
            return; // 会签未集齐，继续等待
        }

        List<WfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
                .eq(WfNode::getProcessId, instance.getProcessId()).orderByAsc(WfNode::getNodeOrder));
        activateFrom(instance, node.getNodeOrder(), nodes);
    }

    /** 当前节点是否集齐：或签任一通过即完成；会签需全部通过 */
    private boolean isNodeComplete(Long instanceId, WfNode node) {
        List<WfTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getInstanceId, instanceId).eq(WfTask::getNodeId, node.getId()));
        if (tasks.isEmpty()) {
            return false;
        }
        if (tasks.stream().anyMatch(t -> t.getStatus() == WorkflowConstants.TASK_REJECTED)) {
            return false;
        }
        boolean allApproved = tasks.stream().allMatch(t -> t.getStatus() == WorkflowConstants.TASK_APPROVED);
        if (WorkflowConstants.MODE_ALL.equals(node.getApproveMode())) {
            return allApproved;
        }
        return tasks.stream().anyMatch(t -> t.getStatus() == WorkflowConstants.TASK_APPROVED);
    }

    // ============================ 撤销 / 转办 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelInstance(Long instanceId, Long operatorId) {
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(RCode.NOT_FOUND, "流程实例不存在");
        }
        if (instance.getStatus() != WorkflowConstants.INSTANCE_RUNNING) {
            throw new BizException(RCode.WORKFLOW_ALREADY_DONE, "流程已结束，不可撤销");
        }
        if (!operatorId.equals(instance.getInitiatorId()) && !isSuperAdmin(operatorId)) {
            throw new BizException(RCode.FORBIDDEN, "仅发起人可撤销该流程");
        }
        instance.setStatus(WorkflowConstants.INSTANCE_CANCELLED);
        instance.setEndTime(LocalDateTime.now());
        instanceMapper.updateById(instance);
        invalidPendingTasks(instanceId, null);
        writeRecord(instanceId, null, null, operatorId, loadName(operatorId),
                WorkflowConstants.OP_CANCEL, "发起人撤销流程");
        callbackDispatcher.onRejected(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferTask(Long taskId, Long operatorId, Long targetUserId) {
        WfTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(RCode.NOT_FOUND, "审批任务不存在");
        }
        if (task.getStatus() != WorkflowConstants.TASK_PENDING) {
            throw new BizException(RCode.WORKFLOW_ALREADY_DONE, "该任务已处理，不可转办");
        }
        if (!operatorId.equals(task.getApproverId()) && !isSuperAdmin(operatorId)) {
            throw new BizException(RCode.FORBIDDEN, "仅当前审批人或超级管理员可转办");
        }
        SysUser target = sysUserMapper.selectById(targetUserId);
        if (target == null) {
            throw new BizException(RCode.NOT_FOUND, "转办目标用户不存在");
        }
        String oldName = task.getApproverName();
        task.setApproverId(targetUserId);
        task.setApproverName(target.getRealName());
        taskMapper.updateById(task);
        writeRecord(task.getInstanceId(), task.getNodeId(), task.getNodeName(), operatorId,
                loadName(operatorId), WorkflowConstants.OP_TRANSFER,
                "由「" + oldName + "」转办给「" + target.getRealName() + "」");
    }

    // ============================ 流程定义管理 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveProcess(WfProcess process) {
        if (process.getProcessKey() == null || process.getProcessKey().isBlank()) {
            throw new BizException(RCode.VALID_ERROR, "流程标识不能为空");
        }
        if (process.getProcessName() == null || process.getProcessName().isBlank()) {
            throw new BizException(RCode.VALID_ERROR, "流程名称不能为空");
        }
        if (process.getId() != null) {
            WfProcess exist = processMapper.selectById(process.getId());
            if (exist == null) {
                throw new BizException(RCode.NOT_FOUND, "流程定义不存在");
            }
            process.setVersion((exist.getVersion() == null ? 1 : exist.getVersion()) + 1);
            processMapper.updateById(process);
            nodeMapper.delete(new LambdaQueryWrapper<WfNode>().eq(WfNode::getProcessId, process.getId()));
            insertNodes(process.getId(), process.getNodes());
            return process.getId();
        }
        if (processMapper.selectOne(new LambdaQueryWrapper<WfProcess>()
                .eq(WfProcess::getProcessKey, process.getProcessKey())) != null) {
            throw new BizException(RCode.BIZ_ERROR, "流程标识已存在：" + process.getProcessKey());
        }
        process.setVersion(1);
        processMapper.insert(process);
        insertNodes(process.getId(), process.getNodes());
        return process.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeProcess(Long id) {
        processMapper.deleteById(id);
        nodeMapper.delete(new LambdaQueryWrapper<WfNode>().eq(WfNode::getProcessId, id));
    }

    private void insertNodes(Long processId, List<WfNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        int order = 1;
        for (WfNode n : nodes) {
            n.setProcessId(processId);
            if (n.getNodeOrder() == null) {
                n.setNodeOrder(order++);
            }
            nodeMapper.insert(n);
        }
    }

    // ============================ 内部工具 ============================

    /** 流程审批通过收尾 */
    private void finishApproved(WfInstance instance) {
        instance.setStatus(WorkflowConstants.INSTANCE_APPROVED);
        instance.setCurrentNodeId(null);
        instance.setCurrentNodeName(null);
        instance.setEndTime(LocalDateTime.now());
        instanceMapper.updateById(instance);
        invalidPendingTasks(instance.getId(), null);
        writeRecord(instance.getId(), null, null, instance.getInitiatorId(), instance.getInitiatorName(),
                WorkflowConstants.OP_FINISH, "全部审批通过，流程结束");
        callbackDispatcher.onApproved(instance);
    }

    /** 将实例下其余待处理任务置为「已失效」 */
    private void invalidPendingTasks(Long instanceId, Long excludeTaskId) {
        List<WfTask> pending = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getInstanceId, instanceId).eq(WfTask::getStatus, WorkflowConstants.TASK_PENDING));
        for (WfTask t : pending) {
            if (excludeTaskId != null && t.getId().equals(excludeTaskId)) {
                continue;
            }
            t.setStatus(WorkflowConstants.TASK_INVALID);
            taskMapper.updateById(t);
        }
    }

    /** 解析节点审批人为用户 id 列表（去重保序） */
    private List<Long> resolveApprovers(WfNode node, Long initiatorDeptId) {
        List<Long> result = new ArrayList<>();
        String type = node.getApproverType();
        String value = node.getApproverValue();
        // 注意：DEPT_LEADER / INITIATOR_LEADER 不依赖 approver_value（按发起人部门负责人解析），
        // 故此处只拦截 type 为空的情形，value 的空值校验下沉到 USER / ROLE 分支单独处理，
        // 否则所有 approver_value 为 NULL 的部门负责人节点都会被错误地判定为「审批人为空」。
        if (type == null) {
            return result;
        }
        switch (type) {
            case WorkflowConstants.APPROVER_USER:
                if (value == null || value.isBlank()) {
                    return result;
                }
                for (String s : value.split(",")) {
                    try {
                        result.add(Long.parseLong(s.trim()));
                    } catch (NumberFormatException ignored) {
                        // 忽略非法 id
                    }
                }
                break;
            case WorkflowConstants.APPROVER_ROLE:
                if (value == null || value.isBlank()) {
                    return result;
                }
                List<Long> roleIds = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getRoleKey, splitTrim(value))).stream()
                        .map(SysRole::getId).collect(Collectors.toList());
                if (!roleIds.isEmpty()) {
                    result.addAll(sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .in(SysUserRole::getRoleId, roleIds)).stream()
                            .map(SysUserRole::getUserId).collect(Collectors.toList()));
                }
                break;
            case WorkflowConstants.APPROVER_DEPT_LEADER:
            case WorkflowConstants.APPROVER_INITIATOR_LEADER:
                if (initiatorDeptId != null) {
                    SysDept dept = sysDeptMapper.selectById(initiatorDeptId);
                    if (dept != null && dept.getLeaderUserId() != null) {
                        result.add(dept.getLeaderUserId());
                    }
                }
                break;
            default:
                break;
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    /** 计算 SpEL 条件是否成立；变量来自 form_data JSON；缺变量或异常视为不成立（跳过节点） */
    private boolean evalCondition(String expr, String formDataJson) {
        if (expr == null || expr.isBlank()) {
            return true;
        }
        try {
            Map<String, Object> vars = parseFormData(formDataJson);
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            if (vars != null) {
                vars.forEach(ctx::setVariable);
            }
            Boolean res = SPEL.parseExpression(expr).getValue(ctx, Boolean.class);
            return Boolean.TRUE.equals(res);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> parseFormData(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>(0);
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new HashMap<>(0);
        }
    }

    private List<String> splitTrim(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private boolean isSuperAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(ErpConstants.SUPER_ADMIN_USER_ID)) {
            return true;
        }
        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)).stream()
                .map(SysUserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return false;
        }
        return sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getRoleKey, ErpConstants.SUPER_ADMIN_ROLE_KEY)) > 0;
    }

    private String loadName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = sysUserMapper.selectById(userId);
        return u == null ? null : u.getRealName();
    }

    private Map<Long, String> loadNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>(0);
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<Long, String> m = new HashMap<>(users.size());
        for (SysUser u : users) {
            m.put(u.getId(), u.getRealName());
        }
        return m;
    }

    private void writeRecord(Long instanceId, Long nodeId, String nodeName,
                             Long operatorId, String operatorName, String operation, String comment) {
        WfRecord r = new WfRecord();
        r.setInstanceId(instanceId);
        r.setNodeId(nodeId);
        r.setNodeName(nodeName);
        r.setOperation(operation);
        r.setOperatorId(operatorId);
        r.setOperatorName(operatorName);
        r.setApproveComment(comment);
        r.setOperTime(LocalDateTime.now());
        recordMapper.insert(r);
    }
}
