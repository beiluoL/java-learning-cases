package com.finpay.business.infrastructure.impl;

import com.finpay.business.infrastructure.entity.PaymentOrder;
import com.finpay.business.infrastructure.mapper.PaymentOrderMapper;
import com.finpay.job.model.ReconciliationBill;
import com.finpay.job.spi.BillSource;
import com.finpay.job.spi.LocalOrderSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对账数据源实现（演示）。
 * - LocalOrderSource：从本地支付订单表取数。
 * - BillSource：演示中返回空列表；真实环境应调用渠道账单下载/API 并解析。
 */
@Component
public class DbReconcileSource implements LocalOrderSource, BillSource {

    private final PaymentOrderMapper orderMapper;

    public DbReconcileSource(PaymentOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public List<ReconciliationBill> fetchLocal(String channel, String date) {
        List<PaymentOrder> orders = orderMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<PaymentOrder>lambdaQuery()
                        .eq(PaymentOrder::getChannel, channel));
        return orders.stream().map(o -> {
            ReconciliationBill b = new ReconciliationBill();
            b.setOutTradeNo(o.getOutTradeNo());
            b.setTradeNo(o.getTradeNo());
            b.setAmount(o.getAmount() != null ? o.getAmount() : BigDecimal.ZERO);
            b.setStatus("SUCCESS".equals(o.getStatus()) ? "SUCCESS" : "FAIL");
            b.setFinishTime(o.getPayTime() != null ? o.getPayTime().toString() : null);
            return b;
        }).collect(Collectors.toList());
    }

    @Override
    public List<ReconciliationBill> fetchBill(String channel, String date) {
        // TODO: 接入支付宝/微信账单下载并解析为 ReconciliationBill 列表
        return List.of();
    }
}
