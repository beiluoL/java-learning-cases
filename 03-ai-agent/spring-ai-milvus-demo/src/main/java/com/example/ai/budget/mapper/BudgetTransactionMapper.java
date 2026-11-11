package com.example.ai.budget.mapper;

import com.example.ai.budget.entity.BudgetTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 预算变更流水 Mapper。 */
@Mapper
public interface BudgetTransactionMapper {

    int insert(BudgetTransaction tx);

    List<BudgetTransaction> listByRequestId(@Param("requestId") String requestId);

    List<BudgetTransaction> page(@Param("budgetType") String budgetType, @Param("targetId") String targetId,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("offset") long offset, @Param("limit") int limit);

    long count(@Param("budgetType") String budgetType, @Param("targetId") String targetId,
               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
