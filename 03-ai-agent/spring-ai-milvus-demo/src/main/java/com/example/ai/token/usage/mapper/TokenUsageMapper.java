package com.example.ai.token.usage.mapper;

import com.example.ai.token.usage.dto.UsageQuery;
import com.example.ai.token.usage.entity.TokenUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 账单 Mapper：只负责 ai_token_usage 的数据访问，SQL 全部在 mapper XML 中。 */
@Mapper
public interface TokenUsageMapper {

    /** 幂等写入：request_id 命中唯一键时不重复插入，也不覆盖已有账单。 */
    int insert(TokenUsage usage);

    TokenUsage findByRequestId(@Param("requestId") String requestId);

    List<TokenUsage> page(@Param("q") UsageQuery q, @Param("offset") long offset, @Param("limit") int limit);

    long count(@Param("q") UsageQuery q);

    /** 对账用：按用户/时间窗汇总真实账单（排除 MOCK 数据）。 */
    BigDecimal sumRealCost(@Param("userId") String userId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    /** 对账用：全部成功调用（含 MOCK）的成本合计，与 Redis 当日 used 口径一致。 */
    BigDecimal sumAllCost(@Param("userId") String userId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);
}
