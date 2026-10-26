package com.finpay.business.web.gateway;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.finpay.business.infrastructure.entity.ChannelConfigEntity;
import com.finpay.business.infrastructure.mapper.ChannelConfigMapper;
import com.finpay.common.result.Result;
import com.finpay.security.audit.AuditLog;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 渠道配置管理（管理后台「渠道配置」页）。演示读写，敏感字段 configJson 需加密（此处明文示意）。
 */
@RestController
@RequestMapping("/api/admin/channel")
@CrossOrigin
public class ChannelConfigAdminController {

    private final ChannelConfigMapper mapper;

    public ChannelConfigAdminController(ChannelConfigMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/list")
    @AuditLog(action = "QUERY", resource = "CHANNEL_CONFIG")
    public Result<?> list() {
        List<ChannelConfigEntity> all = mapper.selectList(null);
        return Result.success(all);
    }

    @PostMapping("/save")
    @AuditLog(action = "CONFIG", resource = "CHANNEL_CONFIG", logParams = false)
    public Result<?> save(@RequestBody ChannelConfigEntity cfg) {
        if (cfg.getId() == null) {
            cfg.setCreateTime(LocalDateTime.now());
            mapper.insert(cfg);
        } else {
            mapper.updateById(cfg);
        }
        return Result.success(cfg.getId());
    }
}
