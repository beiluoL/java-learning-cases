package com.ecommerce.learn.module.seckill.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台保存秒杀活动
 */
@Data
public class SeckillActivitySaveDTO {

    private Long id;
    @NotNull(message = "SPU 不能为空")
    private Long spuId;
    @NotNull(message = "SKU 不能为空")
    private Long skuId;
    private String title;
    @NotNull(message = "秒杀价不能为空")
    private BigDecimal price;
    @NotNull(message = "库存不能为空")
    private Integer stock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
