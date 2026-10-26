package com.finpay.payment.core.api;

import com.finpay.payment.core.exception.ChannelException;
import com.finpay.payment.core.model.dto.*;

/**
 * 统一支付渠道抽象。所有支付渠道（支付宝/微信/银联/自定义）实现本接口，
 * 业务层通过 {@code ChannelManager} 按编码获取实例，对上层完全屏蔽渠道差异。
 *
 * <p>全流程覆盖：下单 → 回调解析验签 → 退款 → 查询。</p>
 */
public interface PaymentChannel {

    /** 渠道编码，如 alipay / wechat */
    String channelCode();

    /**
     * 统一下单。
     * @param req 下单请求
     * @param cfg 渠道配置
     * @return 唤起支付所需信息（二维码/跳转URL/表单）
     */
    PayOrderResult createOrder(PayOrderRequest req, ChannelConfig cfg) throws ChannelException;

    /**
     * 解析并校验渠道异步回调，返回标准化 {@link ChannelCallback}。
     * 实现内部必须完成「验签」与「基础合法性校验」，校验失败抛出异常。
     * @param body    回调原始报文（JSON / 表单串）
     * @param headers 回调请求头（微信等渠道的签名在 header 中，支付宝可忽略）
     * @param cfg     渠道配置
     */
    ChannelCallback parseAndVerifyCallback(String body, java.util.Map<String, String> headers, ChannelConfig cfg) throws ChannelException;

    /** 发起退款 */
    RefundResult refund(RefundRequest req, ChannelConfig cfg) throws ChannelException;

    /** 查询支付订单 */
    QueryResult queryOrder(String outTradeNo, ChannelConfig cfg) throws ChannelException;

    /** 查询退款单 */
    RefundResult queryRefund(String outRefundNo, ChannelConfig cfg) throws ChannelException;
}
