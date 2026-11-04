package com.example.redislab.service;

public interface StockService {

    /** 当前库存 */
    int getCurrentCount(Long productId);

    /** 扣减 1 件：仅当 count>0 时成功，返回是否扣减成功（数据库层兜底防超卖） */
    boolean decrement(Long productId);

    /** 重置库存（演示用） */
    void reset(Long productId, int count);
}
