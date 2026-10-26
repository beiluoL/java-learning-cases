package com.finpay.business.web.gateway;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.finpay.business.infrastructure.entity.PaymentOrder;
import com.finpay.business.infrastructure.entity.RefundOrder;
import com.finpay.business.infrastructure.mapper.PaymentOrderMapper;
import com.finpay.business.infrastructure.mapper.RefundOrderMapper;
import com.finpay.common.exception.BizException;
import com.finpay.common.result.ErrorCode;
import com.finpay.common.util.SnowflakeId;
import com.finpay.payment.core.api.UnifiedPayService;
import com.finpay.payment.core.exception.ChannelException;
import com.finpay.payment.core.model.dto.*;
import com.finpay.payment.core.model.enums.PayStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 支付网关示例业务服务。负责：
 * 1. 生成幂等订单号、落库支付订单。
 * 2. 调用统一支付门面下单，返回唤起支付信息。
 * 3. 处理渠道异步回调（验签后的标准化回调），幂等更新订单状态。
 * 4. 退款、查询。
 */
@Slf4j
@Service
public class PaymentOrderService {

    private final UnifiedPayService unifiedPayService;
    private final PaymentOrderMapper orderMapper;
    private final RefundOrderMapper refundMapper;
    private final SnowflakeId snowflake = new SnowflakeId();

    public PaymentOrderService(UnifiedPayService unifiedPayService,
                               PaymentOrderMapper orderMapper,
                               RefundOrderMapper refundMapper) {
        this.unifiedPayService = unifiedPayService;
        this.orderMapper = orderMapper;
        this.refundMapper = refundMapper;
    }

    /** 下单：先落库再调渠道 */
    @Transactional
    public PayOrderResult createPay(PayOrderRequest req) {
        String outTradeNo = snowflake.nextOrderNo("P");
        PaymentOrder order = new PaymentOrder();
        order.setOutTradeNo(outTradeNo);
        order.setChannel(req.getChannel());
        order.setMerchantNo(req.getMerchantNo());
        order.setAmount(req.getAmount());
        order.setCurrency(req.getCurrency());
        order.setSubject(req.getSubject());
        order.setStatus(PayStatus.INIT.name());
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);

        req.setOutTradeNo(outTradeNo);
        try {
            return unifiedPayService.pay(req);
        } catch (ChannelException e) {
            order.setStatus(PayStatus.FAILED.name());
            orderMapper.updateById(order);
            throw new BizException(ErrorCode.CHANNEL_ORDER_FAIL, e.getMessage());
        }
    }

    /** 处理异步回调（已验签）。幂等：重复回调不重复更新。 */
    @Transactional
    public String handleCallback(String channel, String merchantNo, String body, Map<String, String> headers) {
        ChannelCallback cb = unifiedPayService.handleCallback(channel, merchantNo, body, headers);
        PaymentOrder order = orderMapper.selectOne(
                Wrappers.<PaymentOrder>lambdaQuery().eq(PaymentOrder::getOutTradeNo, cb.getOutTradeNo()));
        if (order == null) {
            throw new BizException(ErrorCode.TRANSACTION_NOT_FOUND, cb.getOutTradeNo());
        }
        // 幂等：已是终态则直接返回成功，避免重复入账
        if (PayStatus.SUCCESS.name().equals(order.getStatus())) {
            return "success";
        }
        order.setTradeNo(cb.getTradeNo());
        order.setCallbackRaw(cb.getRaw());
        if ("SUCCESS".equals(cb.getResult())) {
            order.setStatus(PayStatus.SUCCESS.name());
            order.setPayTime(LocalDateTime.now());
        } else {
            order.setStatus(PayStatus.FAILED.name());
        }
        orderMapper.updateById(order);
        return "success";
    }

    @Transactional
    public RefundResult refund(RefundRequest req) {
        String outRefundNo = snowflake.nextOrderNo("R");
        req.setOutRefundNo(outRefundNo);
        RefundOrder ro = new RefundOrder();
        ro.setOutRefundNo(outRefundNo);
        ro.setOutTradeNo(req.getOutTradeNo());
        ro.setTradeNo(req.getTradeNo());
        ro.setChannel(req.getChannel());
        ro.setRefundAmount(req.getRefundAmount());
        ro.setReason(req.getReason());
        ro.setStatus("PROCESSING");
        ro.setCreateTime(LocalDateTime.now());
        refundMapper.insert(ro);
        try {
            RefundResult r = unifiedPayService.refund(req);
            ro.setStatus(r.getStatus());
            refundMapper.updateById(ro);
            return r;
        } catch (ChannelException e) {
            ro.setStatus("FAILED");
            refundMapper.updateById(ro);
            throw new BizException(ErrorCode.CHANNEL_REFUND_FAIL, e.getMessage());
        }
    }

    public QueryResult query(String channel, String merchantNo, String outTradeNo) {
        return unifiedPayService.queryOrder(channel, merchantNo, outTradeNo);
    }

    /** 交易查询（管理后台用） */
    public List<PaymentOrder> listOrders(String channel, String status, int limit) {
        var q = Wrappers.<PaymentOrder>lambdaQuery();
        if (channel != null) q.eq(PaymentOrder::getChannel, channel);
        if (status != null) q.eq(PaymentOrder::getStatus, status);
        q.orderByDesc(PaymentOrder::getCreateTime).last("LIMIT " + limit);
        return orderMapper.selectList(q);
    }

    /** 金额校验工具（演示用，真实应结合渠道校验） */
    public boolean amountMatches(String outTradeNo, BigDecimal amount) {
        PaymentOrder o = orderMapper.selectOne(
                Wrappers.<PaymentOrder>lambdaQuery().eq(PaymentOrder::getOutTradeNo, outTradeNo));
        return o != null && o.getAmount() != null && o.getAmount().compareTo(amount) == 0;
    }
}
