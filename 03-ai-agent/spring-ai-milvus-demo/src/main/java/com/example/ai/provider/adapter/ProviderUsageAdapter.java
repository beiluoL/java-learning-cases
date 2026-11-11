package com.example.ai.provider.adapter;

import com.example.ai.token.dto.UsageSnapshot;
import org.springframework.ai.chat.model.ChatResponse;

/** 供应商 Usage 适配策略：新增供应商时只加一个实现，不改采集器与业务代码。 */
public interface ProviderUsageAdapter {

    boolean supports(String provider);

    UsageSnapshot extract(ChatResponse response);
}
