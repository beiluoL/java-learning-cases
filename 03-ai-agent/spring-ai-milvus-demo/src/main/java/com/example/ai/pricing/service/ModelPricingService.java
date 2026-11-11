package com.example.ai.pricing.service;

import com.example.ai.common.BusinessException;
import com.example.ai.common.ErrorCode;
import com.example.ai.pricing.entity.ModelPricing;
import com.example.ai.pricing.mapper.ModelPricingMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型价格服务：价格只来自数据库，Java 代码不硬编码任何单价。
 * 查价按「调用时刻生效的版本」匹配，账单保存版本快照，改价不影响历史成本。
 */
@Service
public class ModelPricingService {

    private final ModelPricingMapper mapper;

    public ModelPricingService(ModelPricingMapper mapper) {
        this.mapper = mapper;
    }

    public ModelPricing findEffective(String provider, String modelName, String modelType, LocalDateTime at) {
        return mapper.findEffective(provider, modelName, modelType, at);
    }

    public List<ModelPricing> list() {
        return mapper.listCurrent();
    }

    public List<ModelPricing> history(String modelName) {
        return mapper.history(modelName);
    }

    public ModelPricing get(Long id) {
        ModelPricing p = mapper.findById(id);
        if (p == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "价格记录不存在: " + id);
        }
        return p;
    }

    public void create(ModelPricing p) {
        validate(p);
        p.setId(null);
        p.setEnabled(p.getEnabled() == null ? 1 : p.getEnabled());
        p.setDemoFlag(p.getDemoFlag() == null ? 1 : p.getDemoFlag());
        if (p.getCurrency() == null || p.getCurrency().isBlank()) {
            p.setCurrency("USD");
        }
        mapper.insert(p);
    }

    public void update(Long id, ModelPricing p) {
        ModelPricing existing = get(id);
        validate(p);
        p.setId(id);
        // 禁止改动供应商/模型/版本：历史账单的可追溯性依赖这三项不变
        p.setProvider(existing.getProvider());
        p.setModelName(existing.getModelName());
        p.setVersion(existing.getVersion());
        mapper.update(p);
    }

    private void validate(ModelPricing p) {
        if (p == null || isBlank(p.getProvider()) || isBlank(p.getModelName()) || isBlank(p.getVersion())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "provider / modelName / version 不能为空");
        }
        if (p.getEffectiveFrom() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "effectiveFrom 不能为空");
        }
        if (p.getInputPrice() == null || p.getOutputPrice() == null || p.getCachedInputPrice() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "三个价格字段都必须提供");
        }
        if (p.getInputPrice().signum() < 0 || p.getOutputPrice().signum() < 0 || p.getCachedInputPrice().signum() < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "价格不能为负数");
        }
        if (p.getEffectiveTo() != null && p.getEffectiveTo().isBefore(p.getEffectiveFrom())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "effectiveTo 不能早于 effectiveFrom");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
