package com.example.ai.provider.adapter;

import com.example.ai.common.AiEnums;
import com.example.ai.token.dto.UsageSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OpenAI / DeepSeek 等 OpenAI 兼容接口的真实 Usage 适配。
 * 只读取供应商响应元数据，绝不用字符串长度伪造 Token。
 */
@Component
public class OpenAiUsageAdapter implements ProviderUsageAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(String provider) {
        return "openai".equalsIgnoreCase(provider) || "deepseek".equalsIgnoreCase(provider);
    }

    @Override
    public UsageSnapshot extract(ChatResponse response) {
        ChatResponseMetadata meta = response == null ? null : response.getMetadata();
        String actualModel = meta == null || meta.getModel() == null ? "unknown" : meta.getModel();
        Usage usage = meta == null ? null : meta.getUsage();
        // Spring AI 在未携带 Usage 时会返回全 0 的默认对象，必须一并视作「供应商未提供」，
        // 绝不能把 0/0 包装成 REAL 真实统计。
        if (usage == null || usage.getPromptTokens() == null || usage.getCompletionTokens() == null
                || (usage.getPromptTokens() == 0 && usage.getCompletionTokens() == 0)) {
            return new UsageSnapshot(0, 0, 0, 0, 0,
                    AiEnums.UsageStatus.UNAVAILABLE.name(), actualModel, "openai");
        }
        long input = usage.getPromptTokens().longValue();
        long output = usage.getCompletionTokens().longValue();
        long total = usage.getTotalTokens() == null ? input + output : usage.getTotalTokens().longValue();
        long cached = cachedTokens(usage.getNativeUsage());
        // 缓存 Token 是输入的子集，越界值一律收敛，防止重复计费
        cached = Math.min(Math.max(cached, 0), input);
        return new UsageSnapshot(input, output, total, cached, 0,
                AiEnums.UsageStatus.REAL.name(), actualModel, "openai");
    }

    /** 从 nativeUsage 中尽力提取缓存命中 Token（prompt_tokens_details.cached_tokens）。 */
    private long cachedTokens(Object nativeUsage) {
        if (nativeUsage == null) {
            return 0L;
        }
        try {
            Map<String, Object> map = objectMapper.convertValue(nativeUsage, new TypeReference<>() {
            });
            Object details = map.get("prompt_tokens_details");
            Object cached = null;
            if (details instanceof Map<?, ?> d) {
                cached = d.get("cached_tokens");
            }
            if (cached == null) {
                cached = map.get("cached_tokens");
            }
            // DeepSeek 等兼容接口把缓存命中 Token 放在顶层 prompt_cache_hit_tokens
            if (cached == null) {
                cached = map.get("prompt_cache_hit_tokens");
            }
            return cached instanceof Number n ? n.longValue() : 0L;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
