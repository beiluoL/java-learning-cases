package com.example.ai.budget.mapper;

import com.example.ai.budget.entity.BudgetReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 预扣流水 Mapper。MySQL 是预算流水的审计副本，Redis 才是并发控制的真源。 */
@Mapper
public interface BudgetReservationMapper {

    int insert(BudgetReservation reservation);

    /** 幂等结算/释放：只有 RESERVED 状态可被推进，返回 0 表示已处理过。 */
    int settleOrRelease(@Param("requestId") String requestId,
                        @Param("status") String status,
                        @Param("settledAmount") BigDecimal settledAmount);

    BudgetReservation findByRequestId(@Param("requestId") String requestId);

    List<BudgetReservation> findExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
