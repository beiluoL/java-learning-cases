package com.example.ai.token.analytics.mapper;

import com.example.ai.token.analytics.entity.DailyCostSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 每日成本汇总 Mapper：重建由 SQL 完成（INSERT ... SELECT），服务层不拼 SQL。 */
@Mapper
public interface DailyCostSummaryMapper {

    int rebuildAll(@Param("day") LocalDate day);

    int rebuildByModel(@Param("day") LocalDate day);

    int rebuildByScene(@Param("day") LocalDate day);

    int rebuildByUser(@Param("day") LocalDate day);

    int rebuildByAgent(@Param("day") LocalDate day);

    List<DailyCostSummary> listByRange(@Param("dimension") String dimension,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);
}
