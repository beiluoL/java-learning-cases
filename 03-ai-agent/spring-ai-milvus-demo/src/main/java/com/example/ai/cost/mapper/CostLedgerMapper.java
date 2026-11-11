package com.example.ai.cost.mapper;

import com.example.ai.cost.entity.CostLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 追加式成本账务流水 Mapper：只有 INSERT 与查询，刻意不提供 UPDATE/DELETE。 */
@Mapper
public interface CostLedgerMapper {

    int insert(CostLedger ledger);

    List<CostLedger> listRecent(@Param("limit") int limit);

    List<CostLedger> listByRequestId(@Param("requestId") String requestId);

    /** 按账务口径汇总（ACTUAL / ALLOCATED / SAVED），SAVED 不计入成本合计。 */
    BigDecimal sumByKind(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                         @Param("costKind") String costKind);

    /** 成本构成分析：按 entry_type 分组。 */
    List<Map<String, Object>> breakdown(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("costKind") String costKind);
}
