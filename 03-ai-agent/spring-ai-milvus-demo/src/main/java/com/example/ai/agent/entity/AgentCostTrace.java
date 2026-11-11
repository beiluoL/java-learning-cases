package com.example.ai.agent.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Agent 任务级成本追踪：步数/工具调用/Token/成本聚合，超限即停止。 */
@Data
public class AgentCostTrace {
    /** 主键 */
    private Long id;
    /** Agent 任务 ID，幂等键 */
    private String taskId;
    /** Agent 标识 */
    private String agentId;
    /** 最近一次模型请求 ID */
    private String requestId;
    /** 发起用户 */
    private String userId;
    /** 会话 ID */
    private String conversationId;
    /** 已执行步数 */
    private Integer steps;
    /** 工具调用次数 */
    private Integer toolCalls;
    /** 累计输入 Token */
    private Long inputTokens;
    /** 累计输出 Token */
    private Long outputTokens;
    /** 累计总 Token */
    private Long totalTokens;
    /** 累计成本 */
    private BigDecimal totalCost;
    /** 步数上限 */
    private Integer maxSteps;
    /** RUNNING / COMPLETED / STOPPED / FAILED */
    private String status;
    /** 中止原因：STEP_LIMIT / COST_LIMIT / TOKEN_LIMIT / LOOP_DETECTED / MANUAL / ERROR */
    private String stopReason;
    /** 开始时间(UTC) */
    private LocalDateTime startedAt;
    /** 结束时间(UTC) */
    private LocalDateTime finishedAt;
}
