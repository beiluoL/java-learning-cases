package com.example.mq.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mq.common.enums.OrderStatus;
import com.example.mq.entity.MqOrder;
import com.example.mq.mapper.MqOrderMapper;
import com.example.mq.service.OrderProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时订单兜底补偿（「延迟消息 + 定时扫描」双保险）。
 *
 * <h3>为什么有了延迟消息还需要这个？</h3>
 * 因为延迟消息本身也可能丢：
 * <ul>
 *   <li>事务提交成功、但 afterCommit 里投递延迟消息时 broker 刚好不可用；</li>
 *   <li>延迟队列所在节点磁盘故障且未开镜像队列；</li>
 *   <li>消息被误删（运维误操作 / 队列被 purge）。</li>
 * </ul>
 * 真实生产里几乎都会再加一层"兜底扫描"：按业务字段（expire_time）捞出该处理但没处理的订单，
 * 走同一套幂等的取消逻辑。这正是"最终一致性"的工程写法 ——
 * <b>不追求消息必达，追求状态最终正确</b>。
 *
 * <p>本项目每 60 秒扫一次，一次最多 50 条，避免长时间占用连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutReconcileJob {

    private static final int BATCH_SIZE = 50;

    private final MqOrderMapper orderMapper;
    private final OrderProcessService orderProcessService;

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void reconcile() {
        List<MqOrder> expired;
        try {
            expired = orderMapper.selectList(Wrappers.<MqOrder>lambdaQuery()
                    .eq(MqOrder::getStatus, OrderStatus.UNPAID.name())
                    .lt(MqOrder::getExpireTime, LocalDateTime.now())
                    .orderByAsc(MqOrder::getId)
                    .last("LIMIT " + BATCH_SIZE));
        } catch (Exception e) {
            log.error("[RECONCILE] 扫描超时订单失败", e);
            return;
        }
        if (expired.isEmpty()) {
            return;
        }
        log.warn("[RECONCILE] 发现 {} 笔已超时但仍待支付的订单，开始兜底取消", expired.size());
        for (MqOrder order : expired) {
            try {
                OrderProcessService.CancelOutcome outcome =
                        orderProcessService.cancelOrder(order.getOrderNo(), "定时补偿：超时未支付");
                log.info("[RECONCILE] orderNo={} 结果={}", order.getOrderNo(), outcome);
            } catch (Exception e) {
                log.error("[RECONCILE] 兜底取消失败 orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
