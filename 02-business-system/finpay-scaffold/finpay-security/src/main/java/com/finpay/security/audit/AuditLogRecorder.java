package com.finpay.security.audit;

/**
 * 审计日志记录器契约。安全模块只定义接口，具体落库（MySQL / ES）由各业务工程实现，
 * 保证安全模块与存储解耦。业务模块需提供该接口的 Bean。
 */
public interface AuditLogRecorder {

    /**
     * 异步/同步记录一条审计日志。
     * @param record 审计记录
     */
    void record(AuditLogRecord record);
}
