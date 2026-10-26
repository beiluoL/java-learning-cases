package com.enterprise.erpcrm.module.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.enterprise.erpcrm.module.system.entity.SysOperLog;
import com.enterprise.erpcrm.module.system.mapper.SysOperLogMapper;
import com.enterprise.erpcrm.module.system.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现（由 {@code OperLogAspect} 异步写入）。
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog>
        implements SysOperLogService {
}
