package com.example.csagent.dto;

import com.example.csagent.model.Message;
import lombok.Data;

import java.util.List;

/** 传给 LLM/Agent 生成器的上下文。 */
@Data
public class AgentContext {
    private String userMessage;
    private IntentResult intent;
    private List<FaqMatch> faqs;
    private List<Message> history;
}
