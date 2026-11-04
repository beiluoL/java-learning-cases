package com.example.mq.service;

import com.example.mq.dto.SeckillRequest;
import com.example.mq.dto.SeckillResponse;
import com.example.mq.dto.StressTestResult;

/**
 * 秒杀下单（生产端）。
 */
public interface SeckillService {

    /** 受理下单意向：Redis 预扣 → 落排队单 → 投递 MQ，立即返回 */
    SeckillResponse submit(SeckillRequest request);

    /** 并发压测：用 N 个线程同时打同一件商品，验证「不超卖」 */
    StressTestResult stressTest(Long productId, Integer quantity, Integer concurrency);

    /** 重置某商品的演示库存（DB + Redis），便于反复演示 */
    void resetStock(Long productId);
}
