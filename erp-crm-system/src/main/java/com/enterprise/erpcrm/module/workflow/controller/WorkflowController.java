package com.enterprise.erpcrm.module.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.enterprise.erpcrm.common.result.PageResult;
import com.enterprise.erpcrm.common.result.Result;
import com.enterprise.erpcrm.framework.security.SecurityHolder;
import com.enterprise.erpcrm.framework.security.annotation.OperLog;
import com.enterprise.erpcrm.framework.security.annotation.BusinessType;
import com.enterprise.erpcrm.framework.security.annotation.RequiresPermissions;
import com.enterprise.erpcrm.module.workflow.domain.form.CancelInstanceForm;
import com.enterprise.erpcrm.module.workflow.domain.form.CompleteTaskForm;
import com.enterprise.erpcrm.module.workflow.domain.form.InstanceQuery;
import com.enterprise.erpcrm.module.workflow.domain.form.ProcessQuery;
import com.enterprise.erpcrm.module.workflow.domain.form.StartInstanceForm;
import com.enterprise.erpcrm.module.workflow.domain.form.TaskQuery;
import com.enterprise.erpcrm.module.workflow.domain.form.TransferTaskForm;
import com.enterprise.erpcrm.module.workflow.domain.vo.WfInstanceDetailVo;
import com.enterprise.erpcrm.module.workflow.domain.vo.WfProcessVo;
import com.enterprise.erpcrm.module.workflow.domain.vo.WfTodoTaskVo;
import com.enterprise.erpcrm.module.workflow.entity.WfInstance;
import com.enterprise.erpcrm.module.workflow.entity.WfProcess;
import com.enterprise.erpcrm.module.workflow.service.WorkflowEngineService;
import com.enterprise.erpcrm.module.workflow.service.WorkflowQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批流控制器：流程定义管理、发起/审批/撤销/转办、我的实例与待办。
 * <p>权限标识与系统菜单/角色权限（sys_menu.perms）保持一致。</p>
 *
 * @author erp-crm-system
 */
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngineService engine;
    private final WorkflowQueryService query;

    // ============================ 流程定义 ============================

    /** 流程定义分页列表 */
    @GetMapping("/process/list")
    @RequiresPermissions("workflow:process:list")
    public Result<PageResult<WfProcess>> listProcess(ProcessQuery q) {
        IPage<WfProcess> page = query.pageProcess(q);
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /** 流程定义详情（含节点） */
    @GetMapping("/process/{id}")
    @RequiresPermissions("workflow:process:list")
    public Result<WfProcessVo> getProcess(@PathVariable Long id) {
        return Result.success(query.getProcess(id));
    }

    /** 新增流程定义 */
    @PostMapping("/process")
    @RequiresPermissions("workflow:process:add")
    @OperLog(title = "流程定义", businessType = BusinessType.INSERT)
    public Result<Long> addProcess(@RequestBody WfProcess process) {
        return Result.success(engine.saveProcess(process));
    }

    /** 修改流程定义 */
    @PutMapping("/process")
    @RequiresPermissions("workflow:process:edit")
    @OperLog(title = "流程定义", businessType = BusinessType.UPDATE)
    public Result<Long> editProcess(@RequestBody WfProcess process) {
        return Result.success(engine.saveProcess(process));
    }

    /** 删除流程定义（支持逗号分隔批量） */
    @DeleteMapping("/process/{ids}")
    @RequiresPermissions("workflow:process:remove")
    @OperLog(title = "流程定义", businessType = BusinessType.DELETE)
    public Result<Void> removeProcess(@PathVariable List<Long> ids) {
        ids.forEach(engine::removeProcess);
        return Result.success();
    }

    // ============================ 流程实例 ============================

    /** 发起流程 */
    @PostMapping("/instance/start")
    @RequiresPermissions("workflow:instance:start")
    @OperLog(title = "流程实例", businessType = BusinessType.INSERT)
    public Result<Long> start(@RequestBody StartInstanceForm form) {
        return Result.success(engine.startInstance(form, SecurityHolder.get().getUserId()));
    }

    /** 我发起的流程 */
    @GetMapping("/instance/mine")
    @RequiresPermissions("workflow:instance:mine")
    public Result<PageResult<WfInstance>> mine(InstanceQuery q) {
        IPage<WfInstance> page = query.pageMyInstance(q, SecurityHolder.get().getUserId());
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /** 流程实例详情 */
    @GetMapping("/instance/{id}")
    @RequiresPermissions("workflow:instance:mine")
    public Result<WfInstanceDetailVo> detail(@PathVariable Long id) {
        return Result.success(query.detail(id));
    }

    /** 撤销流程 */
    @PostMapping("/instance/cancel")
    @RequiresPermissions("workflow:instance:cancel")
    public Result<Void> cancel(@RequestBody CancelInstanceForm form) {
        engine.cancelInstance(form.getInstanceId(), SecurityHolder.get().getUserId());
        return Result.success();
    }

    // ============================ 审批任务 ============================

    /** 我的待办 */
    @GetMapping("/task/todo")
    @RequiresPermissions("workflow:task:todo")
    public Result<PageResult<WfTodoTaskVo>> todo(TaskQuery q) {
        IPage<WfTodoTaskVo> page = query.pageMyTodo(q, SecurityHolder.get().getUserId());
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /** 审批（通过/驳回） */
    @PostMapping("/task/complete")
    @RequiresPermissions("workflow:task:approve")
    @OperLog(title = "审批任务", businessType = BusinessType.APPROVE)
    public Result<Void> complete(@RequestBody CompleteTaskForm form) {
        engine.completeTask(form.getTaskId(), SecurityHolder.get().getUserId(),
                Boolean.TRUE.equals(form.getAgree()), form.getComment());
        return Result.success();
    }

    /** 转办 */
    @PostMapping("/task/transfer")
    @RequiresPermissions("workflow:task:transfer")
    public Result<Void> transfer(@RequestBody TransferTaskForm form) {
        engine.transferTask(form.getTaskId(), SecurityHolder.get().getUserId(), form.getTargetUserId());
        return Result.success();
    }
}
