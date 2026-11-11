package com.example.ai.token.usage;

import com.example.ai.common.AiEnums;
import com.example.ai.token.usage.adapter.MockUsageAdapter;
import com.example.ai.token.usage.adapter.OpenAiUsageAdapter;
import com.example.ai.token.usage.dto.UsageSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Usage 解析单测：真实值不伪造、缓存子集收敛、无 Usage 明确标记、Mock 与真实严格区分。 */
class OpenAiUsageAdapterTest {

    private ChatResponse response(Usage usage, String model) {
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder().model(model);
        if (usage != null) {
            builder.usage(usage);
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))), builder.build());
    }

    private Usage usage(Integer in, Integer out, Object nativeUsage) {
        return new Usage() {
            @Override
            public Integer getPromptTokens() {
                return in;
            }

            @Override
            public Integer getCompletionTokens() {
                return out;
            }

            @Override
            public Integer getTotalTokens() {
                return in + out;
            }

            @Override
            public Object getNativeUsage() {
                return nativeUsage;
            }
        };
    }

    @Test
    void realUsageIsExtractedFromProviderMetadata() {
        UsageSnapshot s = new OpenAiUsageAdapter()
                .extract(response(usage(1200, 500, Map.of("prompt_tokens_details", Map.of("cached_tokens", 200))),
                        "deepseek-chat"));
        assertEquals(1200, s.inputTokens());
        assertEquals(500, s.outputTokens());
        assertEquals(1700, s.totalTokens());
        assertEquals(200, s.cachedInputTokens());
        assertEquals("deepseek-chat", s.actualModel());
        assertEquals(AiEnums.UsageStatus.REAL.name(), s.usageStatus());
    }

    @Test
    void missingUsageIsUnavailableNotEstimated() {
        UsageSnapshot s = new OpenAiUsageAdapter().extract(response(null, "gpt-4o-mini"));
        assertEquals(0, s.totalTokens());
        assertEquals(AiEnums.UsageStatus.UNAVAILABLE.name(), s.usageStatus());
    }

    @Test
    void cachedTokensGreaterThanInputAreClamped() {
        UsageSnapshot s = new OpenAiUsageAdapter()
                .extract(response(usage(100, 10, Map.of("prompt_tokens_details", Map.of("cached_tokens", 999))),
                        "m"));
        assertEquals(100, s.cachedInputTokens());
    }

    @Test
    void mockUsageIsNeverMarkedReal() {
        UsageSnapshot s = new MockUsageAdapter().extract(response(usage(32, 16, Map.of()), "mock-chat-model"));
        assertEquals(AiEnums.UsageStatus.MOCK.name(), s.usageStatus());
        assertEquals(48, s.totalTokens());
    }
}
