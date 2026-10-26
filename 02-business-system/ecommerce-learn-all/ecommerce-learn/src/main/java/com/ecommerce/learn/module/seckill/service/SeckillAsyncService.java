package com.ecommerce.learn.module.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.learn.module.order.service.OrderService;
import com.ecommerce.learn.module.seckill.entity.SeckillActivity;
import com.ecommerce.learn.module.seckill.entity.SeckillOrder;
import com.ecommerce.learn.module.seckill.mapper.SeckillActivityMapper;
import com.ecommerce.learn.module.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * 秒杀异步下单服务：将「扣库存成功」后的订单落库放到独立线程池，避免阻塞秒杀主链路。
 *
 * <h3>分布式事务说明</h3>
 * 在真实微服务架构中，「库存服务」与「订单服务」是独立应用、独立数据库，
 * 此时需要分布式事务来保证「下单成功 ⇄ 扣库存成功」的最终一致。
 * 本项目已引入 Seata，只需把下方 {@code @Transactional} 替换为：
 * <pre>
 *   &#64;GlobalTransactional(rollbackFor = Exception.class)   // 需要启动 seata-server
 * </pre>
 * 即可把本地事务升级为跨服务的全局事务（AT 模式自动处理回滚）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillAsyncService {

    private final OrderService orderService;
    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper seckillOrderMapper;

    @Async("seckillExecutor")
    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<Long> createSeckillOrderAsync(Long userId,
                                                           SeckillActivity activity,
                                                           Long skuId,
                                                           int quantity) {
        try {
            Long orderId = orderService.createSeckillOrder(userId, skuId, quantity, activity.getPrice());

            SeckillOrder record = new SeckillOrder();
            record.setActivityId(activity.getId());
            record.setSkuId(skuId);
            record.setUserId(userId);
            record.setOrderId(orderId);
            record.setQuantity(quantity);
            record.setStatus(1);
            seckillOrderMapper.insert(record);

            activityMapper.update(null, Wrappers.<SeckillActivity>lambdaUpdate()
                    .eq(SeckillActivity::getId, activity.getId())
                    .setSql("sold = sold + " + quantity));

            return CompletableFuture.completedFuture(orderId);
        } catch (Exception e) {
            log.error("秒杀异步下单失败 activityId={} userId={}", activity.getId(), userId, e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
