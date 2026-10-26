package com.enterprise.erpcrm.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.erpcrm.common.exception.BizException;
import com.enterprise.erpcrm.common.result.RCode;
import com.enterprise.erpcrm.module.workflow.domain.form.InstanceQuery;
import com.enterprise.erpcrm.module.workflow.domain.form.ProcessQuery;
import com.enterprise.erpcrm.module.workflow.domain.form.TaskQuery;
import com.enterprise.erpcrm.module.workflow.domain.vo.WfInstanceDetailVo;
import com.enterprise.erpcrm.module.workflow.domain.vo.WfProcessVo;
import com.enterprise.erpcrm.module.workflow.domain.vo.WfTodoTaskVo;
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
import com.enterprise.erpcrm.module.workflow.service.WorkflowQueryService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 审批流查询服务实现。
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class WorkflowQueryServiceImpl implements WorkflowQueryService {

    private final WfProcessMapper processMapper;
    private final WfNodeMapper nodeMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final WfRecordMapper recordMapper;

    @Override
    public IPage<WfProcess> pageProcess(ProcessQuery query) {
        LambdaQueryWrapper<WfProcess> w = new LambdaQueryWrapper<>();
        if (query.getProcessKey() != null && !query.getProcessKey().isBlank()) {
            w.eq(WfProcess::getProcessKey, query.getProcessKey());
        }
        if (query.getProcessName() != null && !query.getProcessName().isBlank()) {
            w.like(WfProcess::getProcessName, query.getProcessName());
        }
        if (query.getStatus() != null) {
            w.eq(WfProcess::getStatus, query.getStatus());
        }
        if (query.getCategory() != null && !query.getCategory().isBlank()) {
            w.eq(WfProcess::getCategory, query.getCategory());
        }
        w.orderByDesc(WfProcess::getCreateTime);
        return processMapper.selectPage(query.toPage(), w);
    }

    @Override
    public WfProcessVo getProcess(Long id) {
        WfProcess process = processMapper.selectById(id);
        if (process == null) {
            throw new BizException(RCode.NOT_FOUND, "流程定义不存在");
        }
        List<WfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
                .eq(WfNode::getProcessId, id).orderByAsc(WfNode::getNodeOrder));
        WfProcessVo vo = new WfProcessVo();
        vo.setProcess(process);
        vo.setNodes(nodes);
        return vo;
    }

    @Override
    public IPage<WfInstance> pageMyInstance(InstanceQuery query, Long userId) {
        LambdaQueryWrapper<WfInstance> w = new LambdaQueryWrapper<>();
        w.eq(WfInstance::getInitiatorId, userId);
        if (query.getTitle() != null && !query.getTitle().isBlank()) {
            w.like(WfInstance::getTitle, query.getTitle());
        }
        if (query.getStatus() != null) {
            w.eq(WfInstance::getStatus, query.getStatus());
        }
        if (query.getProcessKey() != null && !query.getProcessKey().isBlank()) {
            w.eq(WfInstance::getProcessKey, query.getProcessKey());
        }
        w.orderByDesc(WfInstance::getCreateTime);
        return instanceMapper.selectPage(query.toPage(), w);
    }

    @Override
    public IPage<WfTodoTaskVo> pageMyTodo(TaskQuery query, Long userId) {
        LambdaQueryWrapper<WfTask> w = new LambdaQueryWrapper<>();
        w.eq(WfTask::getApproverId, userId).eq(WfTask::getStatus, 0);
        if (query.getNodeName() != null && !query.getNodeName().isBlank()) {
            w.like(WfTask::getNodeName, query.getNodeName());
        }
        w.orderByDesc(WfTask::getCreateTime);
        IPage<WfTask> page = taskMapper.selectPage(query.toPage(), w);

        List<WfTodoTaskVo> vos = page.getRecords().stream().map(t -> {
            WfTodoTaskVo vo = new WfTodoTaskVo();
            vo.setTask(t);
            vo.setInstance(instanceMapper.selectById(t.getInstanceId()));
            return vo;
        }).collect(Collectors.toList());

        Page<WfTodoTaskVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public WfInstanceDetailVo detail(Long instanceId) {
        WfInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            throw new BizException(RCode.NOT_FOUND, "流程实例不存在");
        }
        List<WfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
                .eq(WfNode::getProcessId, inst.getProcessId()).orderByAsc(WfNode::getNodeOrder));
        List<WfTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getInstanceId, instanceId).orderByAsc(WfTask::getNodeOrder).orderByAsc(WfTask::getCreateTime));
        List<WfRecord> records = recordMapper.selectList(new LambdaQueryWrapper<WfRecord>()
                .eq(WfRecord::getInstanceId, instanceId).orderByAsc(WfRecord::getOperTime));
        WfInstanceDetailVo vo = new WfInstanceDetailVo();
        vo.setInstance(inst);
        vo.setNodes(nodes);
        vo.setTasks(tasks);
        vo.setRecords(records);
        return vo;
    }
}
