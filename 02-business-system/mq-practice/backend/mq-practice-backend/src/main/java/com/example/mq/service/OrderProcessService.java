package com.example.mq.service;

import com.example.mq.common.message.OrderCreateMessage;

/**
 * 订单「MQ 消费侧」事务核心。
 * <p>
 * 刻意与面向用户的 {@link OrderService} 分开，原因有二：
 * <ol>
 *   <li><b>职责不同</b>：这里是"异步落单 / 超时取消"的补偿逻辑，只被消费者和定时任务调用；</li>
 *   <li><b>事务边界不同</b>：@Transactional 只能包住数据库操作，
 *       而 ACK 必须在事务提交之后 —— 如果把 ack 写进事务方法里，一旦提交失败，
 *       消息已经被删掉了，业务却回滚了，就真丢了。</li>
 * </ol>
 */
public interface OrderProcessService {

    /** 异步落单：扣库存 + 回填订单信息 + 提交后投递延迟消息 */
    CreateOutcome createOrder(OrderCreateMessage message);

    /** 超时/主动取消：状态 CAS + 回补库存 */
    CancelOutcome cancelOrder(String orderNo, String reason);

    /** 落单结果 */
    enum CreateOutcome {
        /** 落单成功（QUEUING → UNPAID） */
        SUCCESS,
        /** 订单已不是 QUEUING（重复消费或已被处理），直接跳过 */
        SKIPPED,
        /** 数据库条件更新未命中：库存真的不够 */
        STOCK_NOT_ENOUGH,
        /** 订单不存在：数据不一致，值得进死信人工排查 */
        ORDER_NOT_FOUND
    }

    /** 取消结果 */
    enum CancelOutcome {
        /** 取消成功（UNPAID → CANCELLED） */
        CANCELLED,
        /** 状态已变（已支付/已取消），跳过 —— 这是延迟消息最常见的正常路径 */
        SKIPPED,
        NOT_FOUND
    }
}
