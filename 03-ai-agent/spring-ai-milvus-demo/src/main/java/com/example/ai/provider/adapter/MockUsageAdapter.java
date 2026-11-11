package com.example.ai.provider.adapter;

import com.example.ai.common.AiEnums;
import com.example.ai.token.dto.UsageSnapshot;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * 模拟模型 Usage 适配。数据来自可控 Mock 响应，但强制标记 MOCK，
 * 使其在对账、真实成本口径与任何生产账单统计中可被明确排除。
 */
@Component
public class MockUsageAdapter implements ProviderUsageAdapter {

    @Override
    public boolean supports(String provider) {
        return "mock".equalsIgnoreCase(provider);
    }

    @Override
    public UsageSnapshot extract(ChatResponse response) {
        ChatResponseMetadata meta = response == null ? null : response.getMetadata();
        String model = meta == null || meta.getModel() == null ? "mock-chat-model" : meta.getModel();
        Usage usage = meta == null ? null : meta.getUsage();
        long input = usage == null || usage.getPromptTokens() == null ? 0L : usage.getPromptTokens().longValue();
        long output = usage == null || usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens().longValue();
        return new UsageSnapshot(input, output, input + output, 0, 0,
                AiEnums.UsageStatus.MOCK.name(), model, "mock");
    }
}
