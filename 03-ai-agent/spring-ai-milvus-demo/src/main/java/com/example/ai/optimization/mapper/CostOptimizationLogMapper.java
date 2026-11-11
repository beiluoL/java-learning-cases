package com.example.ai.optimization.mapper;

import com.example.ai.optimization.entity.CostOptimizationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 成本优化执行记录 Mapper。 */
@Mapper
public interface CostOptimizationLogMapper {

    int insert(CostOptimizationLog log);

    List<Map<String, Object>> sumByStrategy(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<CostOptimizationLog> history(@Param("strategy") String strategy,
                                      @Param("offset") long offset, @Param("limit") int limit);

    long countHistory(@Param("strategy") String strategy);

    BigDecimal sumSavedCost(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
