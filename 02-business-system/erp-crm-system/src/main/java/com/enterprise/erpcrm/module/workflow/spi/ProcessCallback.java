package com.enterprise.erpcrm.module.workflow.spi;

import com.enterprise.erpcrm.module.workflow.entity.WfInstance;

/**
 * 流程回调 SPI：业务模块实现该接口，在流程审批通过/终止时联动更新业务单据状态。
 * <p>引擎在流程「全部节点通过」或「被驳回/撤销」时回调对应实现，
 * 通过 {@code businessType()} 与 {@link WfInstance#getBusinessType()} 匹配。</p>
 * <p>示例：采购单审批通过后，由 inventory 模块的实现将采购单状态置为「已审批」。</p>
 *
 * @author erp-crm-system
 */
public interface ProcessCallback {

    /**
     * 业务类型，须与 wf_process.business_type 一致。
     *
     * @return 业务类型标识
     */
    String businessType();

    /**
     * 流程审批通过（全部节点完成）。默认空实现。
     *
     * @param instance 流程实例
     */
    default void onApproved(WfInstance instance) {
    }

    /**
     * 流程被驳回或撤销。默认空实现。
     *
     * @param instance 流程实例
     */
    default void onRejected(WfInstance instance) {
    }
}
