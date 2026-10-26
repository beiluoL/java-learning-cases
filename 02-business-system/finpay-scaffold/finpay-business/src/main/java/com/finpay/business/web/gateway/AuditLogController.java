package com.finpay.business.web.gateway;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.finpay.business.infrastructure.entity.AuditLogEntity;
import com.finpay.business.infrastructure.mapper.AuditLogMapper;
import com.finpay.common.result.Result;
import com.finpay.security.audit.AuditLog;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审计日志查询（管理后台「审计日志」页）。配合 @AuditLog 切面，满足合规审计要求。
 */
@RestController
@RequestMapping("/api/admin/audit")
@CrossOrigin
public class AuditLogController {

    private final AuditLogMapper mapper;

    public AuditLogController(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/list")
    @AuditLog(action = "QUERY", resource = "AUDIT_LOG")
    public Result<?> list(@RequestParam(required = false) String action,
                          @RequestParam(defaultValue = "200") int limit) {
        var q = Wrappers.<AuditLogEntity>lambdaQuery();
        if (action != null) q.eq(AuditLogEntity::getAction, action);
        q.orderByDesc(AuditLogEntity::getOccurTime).last("LIMIT " + limit);
        List<AuditLogEntity> logs = mapper.selectList(q);
        return Result.success(logs);
    }
}
