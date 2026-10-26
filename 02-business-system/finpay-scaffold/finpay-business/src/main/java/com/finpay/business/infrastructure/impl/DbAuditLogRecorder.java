package com.finpay.business.infrastructure.impl;

import com.finpay.business.infrastructure.entity.AuditLogEntity;
import com.finpay.business.infrastructure.mapper.AuditLogMapper;
import com.finpay.security.audit.AuditLogRecorder;
import com.finpay.security.audit.AuditLogRecord;
import org.springframework.stereotype.Component;

/**
 * 审计日志落库实现。将切面捕获的审计记录写入 {@code t_audit_log}。
 */
@Component
public class DbAuditLogRecorder implements AuditLogRecorder {

    private final AuditLogMapper mapper;

    public DbAuditLogRecorder(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(AuditLogRecord record) {
        AuditLogEntity e = new AuditLogEntity();
        e.setOperator(record.getOperator());
        e.setAction(record.getAction());
        e.setResource(record.getResource());
        e.setBizId(record.getBizId());
        e.setIp(record.getIp());
        e.setChannel(record.getChannel());
        e.setParams(record.getParams());
        e.setResult(record.getResult());
        e.setError(record.getError());
        e.setOccurTime(record.getOccurTime());
        mapper.insert(e);
    }
}
