package com.example.ai.token.analytics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 成本统计 Mapper。所有统计 SQL 都带时间范围与索引字段，返回 Map 由 Service 转成强类型 VO，
 * 避免统计接口全表扫描，也避免把 SQL 写进 Controller / Service。
 */
@Mapper
public interface CostAnalyticsMapper {

    Map<String, Object> overview(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Map<String, Object>> trend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                    @Param("granularity") String granularity);

    List<Map<String, Object>> models(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Map<String, Object>> scenes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Map<String, Object>> topModels(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                        @Param("limit") int limit);

    List<Map<String, Object>> topUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                       @Param("limit") int limit);

    List<Map<String, Object>> hourlyTokenTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 异常检测用：区间内 Token 的均值/峰值/样本数。 */
    Map<String, Object> tokenStats(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 异常检测用：按用户聚合成本，用于识别单用户突增。 */
    List<Map<String, Object>> userCost(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
