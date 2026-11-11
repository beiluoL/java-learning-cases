package com.example.ai.optimization;

import com.example.ai.common.AiEnums;
import com.example.ai.config.AiProperties;
import com.example.ai.pricing.entity.ModelPricing;
import com.example.ai.pricing.service.ModelPricingService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 模型智能路由：按任务复杂度与保护等级在 L1/L2/L3 之间选择。
 *
 * <p>复杂度判定是<b>可解释的启发式规则</b>（长度 + 关键词），不是质量评测模型；
 * 路由正确性必须靠 Golden Dataset 验证，本项目未做该评测，因此：
 * <ul>
 *   <li>默认只做「降级安全」方向的路由（高等级 → 便宜模型），</li>
 *   <li>不做「猜测用户想要更强模型」的升级路由，避免无依据抬高成本。</li>
 * </ul>
 */
@Service
public class ModelRouter {

    private static final Set<String> COMPLEX_HINTS = Set.of(
            "推理", "证明", "对比", "架构", "设计", "为什么", "分析", "权衡", "方案", "重构",
            "prove", "architecture", "trade-off", "compare", "why");

    private final AiProperties properties;
    private final ModelPricingService pricingService;
    private final Clock clock;

    public ModelRouter(AiProperties properties, ModelPricingService pricingService, Clock clock) {
        this.properties = properties;
        this.pricingService = pricingService;
        this.clock = clock;
    }

    public record Decision(String model, AiEnums.RouteLevel level, String reason, boolean downgraded) {
    }

    public Decision route(String message, String scene, AiEnums.ProtectionLevel protectionLevel,
                          boolean forceLowCost) {
        AiProperties.Routing cfg = properties.getRouting();
        String fallback = properties.getModel();
        if (!cfg.isEnabled()) {
            return new Decision(fallback, AiEnums.RouteLevel.L2, "路由已关闭，使用默认模型", false);
        }

        AiEnums.RouteLevel target;
        String reason;
        if (forceLowCost || protectionLevel.code() >= cfg.getDowngradeAtLevel()) {
            target = AiEnums.RouteLevel.L1;
            reason = "保护等级 " + protectionLevel.code() + "，强制降级到低成本模型";
        } else if (AiEnums.BusinessScene.AGENT.name().equalsIgnoreCase(scene)) {
            target = AiEnums.RouteLevel.L2;
            reason = "Agent 任务需要稳定工具调用能力，使用常规档";
        } else if (isComplex(message)) {
            target = AiEnums.RouteLevel.L3;
            reason = "任务复杂度判定为高（长度/关键词命中），使用高能力档";
        } else {
            target = AiEnums.RouteLevel.L2;
            reason = "常规问答，使用标准档";
        }

        String model = modelOf(target, fallback);
        // 路由目标没有生效价格时不能硬用（会导致 UNPRICED 账单），回退默认模型
        if (!hasPricing(model)) {
            if (!model.equals(fallback)) {
                return new Decision(fallback, AiEnums.RouteLevel.L2,
                        reason + "；目标模型无生效价格，回退默认模型 " + fallback, target == AiEnums.RouteLevel.L1);
            }
            return new Decision(fallback, AiEnums.RouteLevel.L2, reason + "；默认模型无价格，账单将标记 UNPRICED",
                    target == AiEnums.RouteLevel.L1);
        }
        boolean downgraded = target == AiEnums.RouteLevel.L1 && !model.equals(fallback);
        return new Decision(model, target, reason, downgraded);
    }

    /** 供管理端展示三档模型配置与价格是否就绪。 */
    public List<ModelRoute> describe() {
        String fallback = properties.getModel();
        return List.of(
                new ModelRoute(AiEnums.RouteLevel.L1.name(), modelOf(AiEnums.RouteLevel.L1, fallback),
                        hasPricing(modelOf(AiEnums.RouteLevel.L1, fallback)), "低成本档"),
                new ModelRoute(AiEnums.RouteLevel.L2.name(), modelOf(AiEnums.RouteLevel.L2, fallback),
                        hasPricing(modelOf(AiEnums.RouteLevel.L2, fallback)), "常规档"),
                new ModelRoute(AiEnums.RouteLevel.L3.name(), modelOf(AiEnums.RouteLevel.L3, fallback),
                        hasPricing(modelOf(AiEnums.RouteLevel.L3, fallback)), "高能力档"));
    }

    public record ModelRoute(String level, String model, boolean pricingReady, String description) {
    }

    boolean isComplex(String message) {
        if (message == null) {
            return false;
        }
        if (message.length() >= properties.getRouting().getComplexCharThreshold()) {
            return true;
        }
        String lower = message.toLowerCase();
        return COMPLEX_HINTS.stream().anyMatch(lower::contains);
    }

    private String modelOf(AiEnums.RouteLevel level, String fallback) {
        AiProperties.Routing cfg = properties.getRouting();
        String configured = switch (level) {
            case L1 -> cfg.getL1Model();
            case L2 -> cfg.getL2Model();
            case L3 -> cfg.getL3Model();
        };
        return configured == null || configured.isBlank() ? fallback : configured.trim();
    }

    private boolean hasPricing(String model) {
        ModelPricing p = pricingService.findEffective(properties.getProvider(), model,
                AiEnums.ModelType.CHAT.name(), LocalDateTime.now(clock));
        return p != null;
    }
}
