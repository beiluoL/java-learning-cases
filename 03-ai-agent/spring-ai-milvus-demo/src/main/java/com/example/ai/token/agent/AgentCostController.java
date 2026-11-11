package com.example.ai.token.agent;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.BusinessException;
import com.example.ai.common.PageResult;
import com.example.ai.token.agent.entity.AgentCostTrace;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Agent 成本追踪查询。
 *
 * <p>说明：本项目未内置 Agent 运行时（工具编排框架），本接口只提供成本追踪与闸门能力的
 * 数据视图；闸门由 {@link AgentCostTracker} 在任何调用前强制执行。
 */
@RestController
@RequestMapping("/api/admin/agent")
public class AgentCostController {

    private final AgentCostTracker tracker;

    public AgentCostController(AgentCostTracker tracker) {
        this.tracker = tracker;
    }

    @GetMapping("/traces")
    public ApiResponse<PageResult<AgentCostTrace>> traces(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long days) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        LocalDateTime to = LocalDateTime.now().plusMinutes(1);
        LocalDateTime from = to.minus(Math.min(Math.max(days == null ? 7 : days, 1), 92), ChronoUnit.DAYS);
        return ApiResponse.ok(new PageResult<>(tracker.page(userId, status, from, to, p, s),
                tracker.count(userId, status, from, to), p, s));
    }

    @GetMapping("/traces/{taskId}")
    public ApiResponse<AgentCostTrace> trace(@PathVariable String taskId) {
        AgentCostTrace t = tracker.get(taskId);
        if (t == null) {
            throw BusinessException.notFound("Agent 任务不存在: " + taskId);
        }
        return ApiResponse.ok(t);
    }
}
