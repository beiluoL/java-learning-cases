package com.example.csagent.service;

import com.example.csagent.dto.AgentContext;

/**
 * 大模型回复生成抽象。两种实现：
 *  - LocalAgentService：本地规则引擎（意图+FAQ 模板），离线可用；
 *  - CodeBuddyBridgeLlmService：调用 CodeBuddy Agent 桥接（Node）获取自然语言回复。
 */
public interface LlmService {
    /** 根据上下文生成回复全文（非流式，由 Controller 负责分片推送）。 */
    String generate(AgentContext ctx);
}
