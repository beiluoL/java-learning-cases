package com.example.ai.agent.mapper;

import com.example.ai.agent.entity.AgentCostTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Agent 任务成本追踪 Mapper。 */
@Mapper
public interface AgentCostTraceMapper {

    int insert(AgentCostTrace trace);

    int update(AgentCostTrace trace);

    AgentCostTrace findByTaskId(@Param("taskId") String taskId);

    List<AgentCostTrace> page(@Param("userId") String userId, @Param("status") String status,
                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                              @Param("offset") long offset, @Param("limit") int limit);

    long count(@Param("userId") String userId, @Param("status") String status,
               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    BigDecimal sumCost(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 异常检测用：指定时间窗内因某原因被中止的任务数（如 LOOP_DETECTED）。 */
    long countStopped(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                      @Param("reason") String reason);
}
