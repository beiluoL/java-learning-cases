package com.example.ragdocqa.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DeepSeekConfig {

    @Value("${llm.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${llm.deepseek.api-key:}")
    private String apiKey;

    @Value("${llm.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${llm.deepseek.temperature:0.3}")
    private double temperature;

    /**
     * 当配置了 DeepSeek API Key 时，手动创建 ChatModel Bean，覆盖默认的 SiliconFlow ChatModel。
     * 这样可以让 embedding 走 SiliconFlow，chat 走 DeepSeek。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "llm.deepseek.enabled", havingValue = "true")
    public OpenAiChatModel deepSeekChatModel() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("llm.deepseek.api-key 未配置");
        }
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }
}
