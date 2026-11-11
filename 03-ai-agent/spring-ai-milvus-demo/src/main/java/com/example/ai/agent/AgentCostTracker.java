package com.example.ai.agent;

import com.example.ai.common.MoneyScale;
import com.example.ai.config.AiProperties;
import com.example.ai.agent.entity.AgentCostTrace;
import com.example.ai.agent.mapper.AgentCostTraceMapper;
import com.example.ai.budget.entity.BudgetPolicy;
import com.example.ai.budget.mapper.BudgetPolicyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 成本追踪与防失控闸门。
 *
 * <p>每次 Agent 内部模型调用都必须先 {@link #guard} 再执行：
 * 步数、工具调用次数、累计 Token、累计成本、执行时长、重复工具调用六道闸门任一触顶即停止，
 * 避免「Agent 循环把预算烧穿」这类最典型的成本事故。
 *
 * <p>状态落 MySQL（可审计）+ 重复调用计数落 Redis（带 TTL 自动过期）。
 */
@Service
public class AgentCostTracker {

    private static final Logger log = LoggerFactory.getLogger(AgentCostTracker.class);
    private static final String P = "ai:{agent}:";

    private final AgentCostTraceMapper traceMapper;
    private final BudgetPolicyMapper policyMapper;
    private final StringRedisTemplate redis;
    private final AiProperties properties;
    private final Clock clock;

    public AgentCostTracker(AgentCostTraceMapper traceMapper, BudgetPolicyMapper policyMapper,
                            StringRedisTemplate redis, AiProperties properties, Clock clock) {
        this.traceMapper = traceMapper;
        this.policyMapper = policyMapper;
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    public record Guard(boolean allowed, String reason) {
    }

    /** 开启一个 Agent 任务追踪；同一 taskId 重复开启幂等。 */
    public AgentCostTrace start(String taskId, String agentId, String userId, String conversationId) {
        AgentCostTrace existing = traceMapper.findByTaskId(taskId);
        if (existing != null) {
            return existing;
        }
        AgentCostTrace t = new AgentCostTrace();
        t.setTaskId(taskId);
        t.setAgentId(agentId == null ? "default" : agentId);
        t.setUserId(userId);
        t.setConversationId(conversationId);
        t.setSteps(0);
        t.setToolCalls(0);
        t.setInputTokens(0L);
        t.setOutputTokens(0L);
        t.setTotalTokens(0L);
        t.setTotalCost(BigDecimal.ZERO);
        t.setMaxSteps(maxSteps());
        t.setStatus("RUNNING");
        t.setStartedAt(LocalDateTime.now(clock));
        traceMapper.insert(t);
        return t;
    }

    /**
     * 执行前闸门：必须在发起 LLM 调用前调用，返回 allowed=false 时绝对不能继续调用。
     *
     * @param nextToolCall 本次将要调用的工具名（可为 null，表示纯 LLM 步骤）
     */
    public Guard guard(String taskId, String nextToolCall) {
        AgentCostTrace t = traceMapper.findByTaskId(taskId);
        if (t == null) {
            return new Guard(false, "TRACE_NOT_FOUND");
        }
        if (!"RUNNING".equals(t.getStatus())) {
            return new Guard(false, "TASK_" + t.getStatus());
        }
        int maxSteps = t.getMaxSteps() == null ? maxSteps() : t.getMaxSteps();
        if (t.getSteps() != null && t.getSteps() >= maxSteps) {
            return stop(t, "STEP_LIMIT");
        }
        int maxToolCalls = maxToolCalls();
        if (t.getToolCalls() != null && t.getToolCalls() >= maxToolCalls) {
            return stop(t, "TOOL_CALL_LIMIT");
        }
        long maxTokens = properties.getAgent().getMaxTokens();
        if (t.getTotalTokens() != null && t.getTotalTokens() >= maxTokens) {
            return stop(t, "TOKEN_LIMIT");
        }
        BigDecimal maxCost = maxCost();
        if (t.getTotalCost() != null && t.getTotalCost().compareTo(maxCost) >= 0) {
            return stop(t, "COST_LIMIT");
        }
        long elapsedSeconds = Duration.between(t.getStartedAt(), LocalDateTime.now(clock)).getSeconds();
        if (elapsedSeconds > properties.getAgent().getMaxDurationSeconds()) {
            return stop(t, "TIME_LIMIT");
        }
        if (nextToolCall != null && repeatedTooMany(taskId, nextToolCall)) {
            return stop(t, "LOOP_DETECTED");
        }
        return new Guard(true, "OK");
    }

    /** 一步完成后累计用量；toolCall 非空同时累加工具调用计数。 */
    public AgentCostTrace recordStep(String taskId, String requestId, long inputTokens, long outputTokens,
                                     BigDecimal cost, String toolCall) {
        AgentCostTrace t = traceMapper.findByTaskId(taskId);
        if (t == null) {
            return null;
        }
        t.setRequestId(requestId);
        t.setSteps((t.getSteps() == null ? 0 : t.getSteps()) + 1);
        if (toolCall != null) {
            t.setToolCalls((t.getToolCalls() == null ? 0 : t.getToolCalls()) + 1);
        }
        t.setInputTokens((t.getInputTokens() == null ? 0 : t.getInputTokens()) + inputTokens);
        t.setOutputTokens((t.getOutputTokens() == null ? 0 : t.getOutputTokens()) + outputTokens);
        t.setTotalTokens((t.getTotalTokens() == null ? 0 : t.getTotalTokens()) + inputTokens + outputTokens);
        t.setTotalCost(MoneyScale.normalize(
                (t.getTotalCost() == null ? BigDecimal.ZERO : t.getTotalCost())
                        .add(cost == null ? BigDecimal.ZERO : cost)));
        if (t.getTotalCost().compareTo(maxCost()) >= 0) {
            t.setStatus("STOPPED");
            t.setStopReason("COST_LIMIT");
        }
        traceMapper.update(t);
        return t;
    }

    public AgentCostTrace finish(String taskId, String status, String stopReason) {
        AgentCostTrace t = traceMapper.findByTaskId(taskId);
        if (t == null) {
            return null;
        }
        t.setStatus(status == null ? "COMPLETED" : status);
        t.setStopReason(stopReason);
        t.setFinishedAt(LocalDateTime.now(clock));
        traceMapper.update(t);
        return t;
    }

    public AgentCostTrace get(String taskId) {
        return traceMapper.findByTaskId(taskId);
    }

    public List<AgentCostTrace> page(String userId, String status, LocalDateTime from, LocalDateTime to,
                                     int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        long offset = (long) (p - 1) * s;
        return traceMapper.page(userId, status, from, to, offset, s);
    }

    public long count(String userId, String status, LocalDateTime from, LocalDateTime to) {
        return traceMapper.count(userId, status, from, to);
    }

    public BigDecimal totalCost(LocalDateTime from, LocalDateTime to) {
        BigDecimal v = traceMapper.sumCost(from, to);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 连续相同工具调用检测：同一个工具连续调用超过阈值即判定循环。 */
    private boolean repeatedTooMany(String taskId, String toolName) {
        try {
            String key = P + "loop:" + taskId + ":" + toolName;
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) {
                redis.expire(key, Duration.ofMinutes(30));
            }
            return n != null && n > properties.getAgent().getMaxRepeatedToolCalls();
        } catch (DataAccessException e) {
            log.warn("循环检测 Redis 异常，放行由步数上限兜底: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private Guard stop(AgentCostTrace t, String reason) {
        t.setStatus("STOPPED");
        t.setStopReason(reason);
        t.setFinishedAt(LocalDateTime.now(clock));
        traceMapper.update(t);
        log.warn("Agent 任务 {} 触发成本闸门: {}", t.getTaskId(), reason);
        return new Guard(false, reason);
    }

    private int maxSteps() {
        BudgetPolicy p = policyMapper.findScope("AGENT", "__ALL__");
        return p != null && p.getMaxAgentSteps() != null ? p.getMaxAgentSteps() : properties.getAgent().getMaxSteps();
    }

    private int maxToolCalls() {
        BudgetPolicy p = policyMapper.findScope("AGENT", "__ALL__");
        return p != null && p.getMaxToolCalls() != null ? p.getMaxToolCalls() : properties.getAgent().getMaxToolCalls();
    }

    private BigDecimal maxCost() {
        BudgetPolicy p = policyMapper.findScope("AGENT", "__ALL__");
        return p != null && p.getPerRequestLimit() != null
                ? p.getPerRequestLimit() : properties.getAgent().getMaxCost();
    }
}
