package com.example.mq.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mq.common.enums.OrderStatus;
import com.example.mq.common.message.OrderCancelMessage;
import com.example.mq.common.message.OrderCreateMessage;
import com.example.mq.common.util.Ids;
import com.example.mq.config.MqProperties;
import com.example.mq.entity.MqOrder;
import com.example.mq.entity.MqProduct;
import com.example.mq.mapper.MqOrderMapper;
import com.example.mq.mapper.MqProductMapper;
import com.example.mq.mapper.MqStockMapper;
import com.example.mq.mq.AfterCommit;
import com.example.mq.mq.DelayMessagePublisher;
import com.example.mq.mq.RedisStockService;
import com.example.mq.service.OrderProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单处理核心 —— 本项目最需要读懂的一个类。
 *
 * <h3>事务边界与 MQ 的配合（生产级要点）</h3>
 * <pre>
 *  ┌──────────────── @Transactional ────────────────┐
 *  │ 1. 校验订单状态（QUEUING 才继续）               │
 *  │ 2. 条件更新扣库存（available >= qty）           │
 *  │ 3. CAS 回填订单 UNPAID + expire_time           │
 *  │ 4. 注册 afterCommit：投递延迟取消消息            │
 *  └────────────────────────────────────────────────┘
 *            │ commit 成功
 *            ▼
 *        投递延迟消息  →  消费者 ACK
 *
 * 任何一步抛异常 → 整个事务回滚 → 消费者不 ACK → 消息进重试队列。
 * 这就是"业务与消息同生共死"的基本盘。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessServiceImpl implements OrderProcessService {

    private final MqOrderMapper orderMapper;
    private final MqStockMapper stockMapper;
    private final MqProductMapper productMapper;
    private final RedisStockService redisStockService;
    private final DelayMessagePublisher delayMessagePublisher;
    private final MqProperties mqProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOutcome createOrder(OrderCreateMessage message) {
        String orderNo = message.getOrderNo();

        MqOrder order = findByOrderNo(orderNo);
        if (order == null) {
            log.error("[ORDER-CREATE] 消息对应的订单不存在 orderNo={} messageId={}", orderNo, message.getMessageId());
            return CreateOutcome.ORDER_NOT_FOUND;
        }
        if (!OrderStatus.QUEUING.name().equals(order.getStatus())) {
            // 幂等之外的二次保险：状态机已经流转过，不再重复落单
            log.warn("[ORDER-CREATE] 订单状态已是 {}，跳过 orderNo={}", order.getStatus(), orderNo);
            return CreateOutcome.SKIPPED;
        }

        int quantity = order.getQuantity() == null ? 1 : order.getQuantity();

        // ① 数据库条件更新扣库存：真正的防超卖闸门
        int deducted = stockMapper.deduct(order.getProductId(), quantity);
        if (deducted == 0) {
            // Redis 预扣成功但 DB 不足（缓存与库不一致 / 多实例竞争）→ 订单失败并回补 Redis
            orderMapper.markFailed(orderNo, "库存不足");
            AfterCommit.run(() -> redisStockService.restore(order.getProductId(), quantity));
            log.warn("[ORDER-CREATE] 库存不足，订单置为 FAILED orderNo={} productId={} qty={}",
                    orderNo, order.getProductId(), quantity);
            return CreateOutcome.STOCK_NOT_ENOUGH;
        }

        MqProduct product = productMapper.selectById(order.getProductId());
        if (product == null) {
            // 商品被删除：抛异常让事务回滚（库存扣减也会回滚），消息进入重试/死信
            throw new IllegalStateException("商品不存在，无法落单 productId=" + order.getProductId());
        }

        int delaySeconds = resolveDelaySeconds(message.getDelaySeconds());
        BigDecimal amount = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(delaySeconds);

        // ② CAS 回填：只有 QUEUING 能变成 UNPAID
        int confirmed = orderMapper.confirmCreated(orderNo, amount, product.getName(), expireTime, delaySeconds);
        if (confirmed == 0) {
            log.warn("[ORDER-CREATE] 状态流转失败（并发/重复消费）orderNo={}", orderNo);
            // 抛异常回滚库存扣减，避免"扣了库存但订单没变成 UNPAID"
            throw new IllegalStateException("订单状态流转失败 orderNo=" + orderNo);
        }

        // ③ 提交后再投递延迟消息 —— 避免"取消消息比订单提交更早到达"
        OrderCancelMessage cancelMessage = OrderCancelMessage.builder()
                .messageId(Ids.messageId())
                .orderNo(orderNo)
                .productId(order.getProductId())
                .quantity(quantity)
                .createTime(order.getCreateTime())
                .build();
        AfterCommit.run(() -> delayMessagePublisher.publish(cancelMessage, delaySeconds));

        log.info("[ORDER-CREATE] 落单成功 orderNo={} amount={} expireAt={}（{}s 后自动取消）",
                orderNo, amount, expireTime, delaySeconds);
        return CreateOutcome.SUCCESS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CancelOutcome cancelOrder(String orderNo, String reason) {
        MqOrder order = findByOrderNo(orderNo);
        if (order == null) {
            log.error("[ORDER-CANCEL] 订单不存在 orderNo={}", orderNo);
            return CancelOutcome.NOT_FOUND;
        }
        if (!OrderStatus.UNPAID.name().equals(order.getStatus())) {
            // 已支付 / 已取消：延迟消息到这里属于正常现象，直接跳过
            log.info("[ORDER-CANCEL] 订单状态为 {}，无需取消 orderNo={}", order.getStatus(), orderNo);
            return CancelOutcome.SKIPPED;
        }

        // ① CAS 取消：若此刻用户刚好支付成功，这里会返回 0，绝不会误取消
        int cancelled = orderMapper.markCancelled(orderNo, reason);
        if (cancelled == 0) {
            log.warn("[ORDER-CANCEL] CAS 取消未命中（可能刚被支付）orderNo={}", orderNo);
            return CancelOutcome.SKIPPED;
        }

        int quantity = order.getQuantity() == null ? 1 : order.getQuantity();
        // ② 回补数据库库存（与扣减用同一个方法族，保证版本号/时间一致）
        stockMapper.restore(order.getProductId(), quantity);
        // ③ 提交后回补 Redis 秒杀库存
        AfterCommit.run(() -> redisStockService.restore(order.getProductId(), quantity));

        log.info("[ORDER-CANCEL] 订单已取消并回补库存 orderNo={} productId={} qty={} reason={}",
                orderNo, order.getProductId(), quantity, reason);
        return CancelOutcome.CANCELLED;
    }

    private MqOrder findByOrderNo(String orderNo) {
        return orderMapper.selectOne(Wrappers.<MqOrder>lambdaQuery().eq(MqOrder::getOrderNo, orderNo));
    }

    private int resolveDelaySeconds(Integer fromMessage) {
        if (fromMessage == null || fromMessage <= 0) {
            return mqProperties.getOrderDelaySeconds();
        }
        return fromMessage;
    }
}
