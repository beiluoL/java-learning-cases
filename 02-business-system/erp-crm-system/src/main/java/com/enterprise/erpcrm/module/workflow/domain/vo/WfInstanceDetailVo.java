package com.enterprise.erpcrm.module.workflow.domain.vo;

import com.enterprise.erpcrm.module.workflow.entity.WfInstance;
import com.enterprise.erpcrm.module.workflow.entity.WfNode;
import com.enterprise.erpcrm.module.workflow.entity.WfRecord;
import com.enterprise.erpcrm.module.workflow.entity.WfTask;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程实例详情 VO（实例 + 节点 + 任务 + 审批流水）。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class WfInstanceDetailVo implements Serializable {

    private WfInstance instance;
    private List<WfNode> nodes;
    private List<WfTask> tasks;
    private List<WfRecord> records;
}
