package com.example.csagent.service;

import com.example.csagent.dto.AgentContext;

import java.util.function.Consumer;

/**
 * 大模型回复生成抽象。两种实现：
 *  - LocalAgentService：本地规则引擎（意图+FAQ 模板），离线可用；
 *  - CodeBuddyBridgeLlmService：调用 CodeBuddy Agent 桥接（Node）获取自然语言回复（真实流式）。
 *
 * 流式说明：
 *  - {@link #generate} 返回全文（非流式场景 / 兜底用）；
 *  - {@link #streamGenerate} 逐 token 回调，默认实现是对 generate 的结果做分片推送，
 *    真实实现（CodeBuddyBridgeLlmService）会消费桥接的 SSE，把大模型真实产出的 token 实时回调。
 */
public interface LlmService {

    /** 根据上下文生成回复全文（非流式）。 */
    String generate(AgentContext ctx);

    /** 逐 token 流式生成；默认实现为对全文按 3 字分片 + 小幅节流，子类可重写为真实流式。 */
    default void streamGenerate(AgentContext ctx, Consumer<String> onToken) {
        String full = generate(ctx);
        if (full == null || full.isBlank()) return;
        int i = 0;
        int n = full.length();
        while (i < n) {
            int end = Math.min(i + 3, n);
            onToken.accept(full.substring(i, end));
            i = end;
            try {
                Thread.sleep(18);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
