package com.enterprise.erpcrm.module.system.service.impl;

import com.enterprise.erpcrm.framework.security.annotation.OperLogRecord;
import com.enterprise.erpcrm.framework.security.annotation.OperLogSink;
import com.enterprise.erpcrm.module.system.entity.SysOperLog;
import com.enterprise.erpcrm.module.system.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 操作日志持久化实现：将切面采集的记录写入 {@code sys_oper_log} 表。
 *
 * @author erp-crm-system
 */
@Component
@RequiredArgsConstructor
public class DbOperLogSink implements OperLogSink {

    private final SysOperLogService operLogService;

    @Override
    public void save(OperLogRecord record) {
        SysOperLog entity = new SysOperLog();
        entity.setTitle(record.getTitle());
        entity.setBusinessType(record.getBusinessType());
        entity.setMethod(record.getMethod());
        entity.setRequestMethod(record.getRequestMethod());
        entity.setOperUrl(record.getOperUrl());
        entity.setOperParam(record.getOperParam());
        entity.setStatus(record.getStatus());
        entity.setErrorMsg(record.getErrorMsg());
        entity.setOperTime(record.getOperTime());
        entity.setCostTime(record.getCostTime());
        entity.setOperName(record.getOperName());
        entity.setOperIp(record.getOperIp());
        if (record.getOperUserId() != null) {
            entity.setOperUserId(record.getOperUserId());
        }
        operLogService.save(entity);
    }
}
