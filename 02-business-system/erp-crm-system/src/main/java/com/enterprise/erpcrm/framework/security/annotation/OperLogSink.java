package com.enterprise.erpcrm.framework.security.annotation;

/**
 * 操作日志持久化契约（依赖倒置）：framework 的 {@code OperLogAspect} 仅依赖此接口，
 * 具体入库由 system 模块实现，避免 framework 反向依赖业务模块。
 *
 * @author erp-crm-system
 */
public interface OperLogSink {

    /** 保存一条操作日志（实现内部自行异步化） */
    void save(OperLogRecord record);
}
