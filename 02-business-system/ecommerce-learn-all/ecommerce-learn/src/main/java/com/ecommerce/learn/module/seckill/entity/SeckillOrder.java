package com.ecommerce.learn.module.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 秒杀订单记录（用于幂等去重与统计）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_seckill_order")
public class SeckillOrder extends BaseEntity {

    private Long activityId;
    private Long skuId;
    private Long userId;
    private Long orderId;
    private Integer quantity;
    /** 1 成功 */
    private Integer status;
}
