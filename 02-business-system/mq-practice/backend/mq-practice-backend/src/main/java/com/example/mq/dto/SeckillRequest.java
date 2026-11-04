package com.example.mq.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 秒杀下单请求 */
@Data
public class SeckillRequest {

    @NotNull(message = "商品 ID 不能为空")
    private Long productId;

    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = 5, message = "单次最多购买 5 件")
    private Integer quantity = 1;

    /** 模拟用户 ID；不传则随机生成，方便压测区分 */
    private Long userId;

    /**
     * 支付超时秒数。默认取配置 1800 秒（30 分钟）。
     * 演示"超时自动取消"时传 10，就能在页面上几十秒内看到完整链路。
     */
    @Min(value = 1, message = "延迟至少 1 秒")
    @Max(value = 86400, message = "延迟最多 1 天")
    private Integer delaySeconds;
}
