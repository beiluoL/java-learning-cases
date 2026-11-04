package com.example.mq.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mq.common.api.PageResult;
import com.example.mq.common.api.ResultCode;
import com.example.mq.common.enums.OrderStatus;
import com.example.mq.common.exception.BusinessException;
import com.example.mq.dto.OrderView;
import com.example.mq.entity.MqOrder;
import com.example.mq.mapper.MqOrderMapper;
import com.example.mq.mapper.MqStockMapper;
import com.example.mq.mq.AfterCommit;
import com.example.mq.mq.RedisStockService;
import com.example.mq.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final MqOrderMapper orderMapper;
    private final MqStockMapper stockMapper;
    private final RedisStockService redisStockService;

    @Override
    public PageResult<OrderView> page(String status, String keyword, Long userId, long current, long size) {
        Page<MqOrder> page = new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 100));
        IPage<MqOrder> result = orderMapper.selectOrderPage(page, status, keyword, userId);
        List<OrderView> records = result.getRecords().stream()
                .map(OrderView::from)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public OrderView detail(String orderNo) {
        MqOrder order = orderMapper.selectOne(Wrappers.<MqOrder>lambdaQuery().eq(MqOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return OrderView.from(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderView pay(String orderNo) {
        MqOrder order = orderMapper.selectOne(Wrappers.<MqOrder>lambdaQuery().eq(MqOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        // 条件更新：只有 UNPAID 能被支付。与"超时取消消费者"形成互斥
        int updated = orderMapper.markPaid(orderNo);
        if (updated == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单当前状态为【" + OrderStatus.valueOf(order.getStatus()).getDescription() + "】，无法支付");
        }
        log.info("[ORDER-PAY] 支付成功 orderNo={}（30 分钟后的取消消息将自动跳过）", orderNo);
        return OrderView.from(orderMapper.selectOne(
                Wrappers.<MqOrder>lambdaQuery().eq(MqOrder::getOrderNo, orderNo)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderView cancel(String orderNo, String reason) {
        MqOrder order = orderMapper.selectOne(Wrappers.<MqOrder>lambdaQuery().eq(MqOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        int updated = orderMapper.markCancelled(orderNo, reason == null ? "用户主动取消" : reason);
        if (updated == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单当前状态为【" + OrderStatus.valueOf(order.getStatus()).getDescription() + "】，无法取消");
        }
        int quantity = order.getQuantity() == null ? 1 : order.getQuantity();
        stockMapper.restore(order.getProductId(), quantity);
        AfterCommit.run(() -> redisStockService.restore(order.getProductId(), quantity));
        log.info("[ORDER-CANCEL] 主动取消完成 orderNo={} reason={}", orderNo, reason);
        return OrderView.from(orderMapper.selectOne(
                Wrappers.<MqOrder>lambdaQuery().eq(MqOrder::getOrderNo, orderNo)));
    }

    @Override
    public Map<String, Long> countByStatus() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            Long count = orderMapper.selectCount(
                    Wrappers.<MqOrder>lambdaQuery().eq(MqOrder::getStatus, status.name()));
            result.put(status.name(), count == null ? 0L : count);
        }
        return result;
    }
}
