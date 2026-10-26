package com.enterprise.erpcrm.module.workflow.domain.vo;

import com.enterprise.erpcrm.module.workflow.entity.WfNode;
import com.enterprise.erpcrm.module.workflow.entity.WfProcess;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程定义详情 VO（流程 + 有序节点）。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class WfProcessVo implements Serializable {

    private WfProcess process;
    private List<WfNode> nodes;
}
