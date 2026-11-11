package com.example.ai.cost.mapper;

import com.example.ai.cost.entity.InfrastructureCost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 基础设施成本 Mapper。 */
@Mapper
public interface InfrastructureCostMapper {

    List<InfrastructureCost> list();

    InfrastructureCost findById(@Param("id") Long id);

    int insert(InfrastructureCost cost);

    int update(InfrastructureCost cost);

    /** 周期内总成本，用于计算单位请求分摊成本。 */
    BigDecimal sumAmount(@Param("start") LocalDate start, @Param("end") LocalDate end);

    BigDecimal sumAllocated(@Param("start") LocalDate start, @Param("end") LocalDate end);

    int updateAllocated(@Param("id") Long id, @Param("allocatedAmount") BigDecimal allocatedAmount,
                        @Param("requestCount") Long requestCount);
}
