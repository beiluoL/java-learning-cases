package com.example.ai.chat.model;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

/**
 * 可控 Mock 模型：无 API Key 时也能完成完整业务链路的自动化测试。
 * 返回的 Usage 由 MockUsageAdapter 强制标记 MOCK，不进入真实账单口径。
 * 流式调用不提供 Usage，因此明确拒绝，避免假装已准确统计。
 */
public class MockChatModel implements ChatModel {

    public static final String MODEL_NAME = "mock-chat-model";

    @Override
    public ChatResponse call(Prompt prompt) {
        String answer = "【MOCK 模拟回答】本回答由本地可控 Mock 模型生成，用于验证预算、Usage 采集、成本计算与账单持久化链路。"
                + "它不是真实供应商调用，不得计入真实成本口径。";
        Usage usage = new Usage() {
            @Override
            public Integer getPromptTokens() {
                return 32;
            }

            @Override
            public Integer getCompletionTokens() {
                return 16;
            }

            @Override
            public Integer getTotalTokens() {
                return 48;
            }

            @Override
            public Object getNativeUsage() {
                return Map.of("mock", true);
            }
        };
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(MODEL_NAME)
                .usage(usage)
                .keyValue("mock", true)
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))), metadata);
    }
}
