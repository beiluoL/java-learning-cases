package com.example.ai.budget;

import java.math.BigDecimal;

/**
 * 预算结算完成事件。
 *
 * <p>用事件而不是直接方法调用，是为了避免构造期循环依赖：
 * 阈值预警评估器需要读 {@link TokenBudgetService#status(String)} 才能算出使用率，
 * 而触发评估的动作恰恰又发生在结算之后。事件在分发时才解析监听器，天然打破这个环。
 *
 * <p>监听方必须保证「失败不影响主链路」——告警只是旁路观测，不能反过来让结算失败。
 */
public record BudgetSettledEvent(String requestId, String userId, BigDecimal actual) {
}
