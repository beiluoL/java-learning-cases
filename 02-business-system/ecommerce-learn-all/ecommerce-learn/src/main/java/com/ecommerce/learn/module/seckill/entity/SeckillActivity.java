package com.ecommerce.learn.module.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_seckill_activity")
public class SeckillActivity extends BaseEntity {

    private Long spuId;
    private Long skuId;
    /** 活动标题 */
    private String title;
    /** 秒杀价 */
    private BigDecimal price;
    /** 秒杀总库存 */
    private Integer stock;
    /** 已售数量 */
    private Integer sold;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 状态：0 未开始，1 进行中，2 已结束 */
    private Integer status;
}
