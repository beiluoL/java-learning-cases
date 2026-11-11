package com.example.ai.config;

import com.example.ai.chat.model.MockChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 模型装配：默认 mock（无 Key 也可启动），ai.provider=openai 时走 OpenAI 兼容接口。
 * 密钥只从环境变量注入的配置读取，代码与配置文件中均无明文 Key。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiModelConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ChatModel chatModel(AiProperties properties) {
        if (!"openai".equalsIgnoreCase(properties.getProvider())) {
            return new MockChatModel();
        }
        String key = properties.getApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("ai.provider=openai 但未提供 AI_API_KEY，请改用默认 mock 或配置环境变量");
        }
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(key)
                .baseUrl(properties.getBaseUrl())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(properties.getModel()).build())
                .build();
    }
}
