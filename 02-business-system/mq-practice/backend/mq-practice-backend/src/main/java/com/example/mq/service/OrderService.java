package com.example.mq.service;

import com.example.mq.common.api.PageResult;
import com.example.mq.dto.OrderView;

import java.util.Map;

/**
 * 面向用户的订单操作（查询/支付/取消）。
 */
public interface OrderService {

    PageResult<OrderView> page(String status, String keyword, Long userId, long current, long size);

    OrderView detail(String orderNo);

    /** 支付：CAS UNPAID → PAID。支付成功后，30 分钟的延迟消息到点会因状态不对而自动跳过 */
    OrderView pay(String orderNo);

    /** 用户主动取消：CAS UNPAID → CANCELLED 并回补库存 */
    OrderView cancel(String orderNo, String reason);

    /** 各状态订单数量（看板用） */
    Map<String, Long> countByStatus();
}
