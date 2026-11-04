package com.example.mq.mq;

import lombok.Getter;

/** Redis 预扣库存的结果语义：把"魔法返回值"封装成枚举，前端/日志都可读 */
@Getter
public enum StockDeductResult {

    /** 扣减成功，返回剩余库存 */
    SUCCESS("扣减成功"),

    /** 库存不足（Redis 已判定售罄，直接快速失败，不打扰数据库） */
    SOLD_OUT("商品已售罄"),

    /** Redis 里没有该商品的库存（未预热或已过期）→ 降级为「只走数据库」 */
    NOT_INITIALIZED("库存未预热");

    private final String description;

    StockDeductResult(String description) {
        this.description = description;
    }
}
