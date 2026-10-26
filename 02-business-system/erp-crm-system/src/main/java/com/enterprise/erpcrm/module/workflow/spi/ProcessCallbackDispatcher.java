package com.enterprise.erpcrm.module.workflow.spi;

import com.enterprise.erpcrm.module.workflow.entity.WfInstance;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 流程回调分发器：收集所有 {@link ProcessCallback} 实现，按 businessType 路由。
 * <p>若某业务类型无对应实现（如本仓库业务模块尚未接入），回调静默跳过，不影响流程流转。</p>
 *
 * @author erp-crm-system
 */
@Component
public class ProcessCallbackDispatcher {

    private final Map<String, ProcessCallback> callbackMap;

    public ProcessCallbackDispatcher(List<ProcessCallback> callbacks) {
        this.callbackMap = callbacks.stream()
                .collect(Collectors.toMap(ProcessCallback::businessType, c -> c, (a, b) -> a));
    }

    /** 流程审批通过回调 */
    public void onApproved(WfInstance instance) {
        of(instance).ifPresent(c -> c.onApproved(instance));
    }

    /** 流程驳回/撤销回调 */
    public void onRejected(WfInstance instance) {
        of(instance).ifPresent(c -> c.onRejected(instance));
    }

    private Optional<ProcessCallback> of(WfInstance instance) {
        if (instance == null || instance.getBusinessType() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(callbackMap.get(instance.getBusinessType()));
    }
}
