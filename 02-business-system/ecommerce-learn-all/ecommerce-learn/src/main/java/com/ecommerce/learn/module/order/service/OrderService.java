package com.ecommerce.learn.module.order.service;

import com.ecommerce.learn.common.result.PageResult;
import com.ecommerce.learn.module.order.dto.*;
import com.ecommerce.learn.module.pay.dto.PayInfoVO;

import java.math.BigDecimal;

public interface OrderService {

    /** 创建订单（本地事务保证原子性 + 库存预占） */
    OrderCreateVO createOrder(Long userId, CreateOrderDTO dto);

    /** 发起支付，返回支付参数 */
    PayInfoVO pay(Long userId, Long orderId, String channel);

    /** 支付成功回调：标记已支付并扣减锁定库存（幂等） */
    void markPaid(String orderNo, String tradeNo);

    /** 取消订单：释放预占库存 */
    void cancel(Long userId, Long orderId);

    /** 确认收货 */
    void confirm(Long userId, Long orderId);

    /** 订单详情（校验归属） */
    OrderVO detail(Long userId, Long orderId);

    /** 我的订单 */
    PageResult<OrderVO> myOrders(Long userId, long current, long size);

    /** 后台发货 */
    void ship(Long orderId, String logisticsCompany, String logisticsNo);

    /** 后台订单列表 */
    PageResult<OrderVO> adminList(long current, long size);

    /**
     * 秒杀专用下单：Redis 已原子扣减库存，这里扣 DB 库存并落库订单（状态=已支付）
     * @param price 秒杀价（与 SKU 原价可能不同）
     */
    Long createSeckillOrder(Long userId, Long skuId, int quantity, BigDecimal price);
}
