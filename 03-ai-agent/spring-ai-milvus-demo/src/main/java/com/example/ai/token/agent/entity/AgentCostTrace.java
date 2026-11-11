package com.example.ai.token.agent.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Agent 任务级成本追踪：步数/工具调用/Token/成本聚合，超限即停止。 */
@Data
public class AgentCostTrace {
    private Long id;
    private String taskId;
    private String agentId;
    private String requestId;
    private String userId;
    private String conversationId;
    private Integer steps;
    private Integer toolCalls;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private BigDecimal totalCost;
    private Integer maxSteps;
    private String status;
    private String stopReason;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
