package com.finpay.payment.alipay.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.finpay.payment.core.api.PaymentChannel;
import com.finpay.payment.core.exception.ChannelException;
import com.finpay.payment.core.model.dto.*;
import com.finpay.payment.core.model.enums.PayStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝渠道实现。基于 alipay-sdk-java，覆盖：扫码/网站下单、回调验签、退款、查询。
 * 支持沙箱（sandbox=true 时使用 alipaydev 网关）。
 *
 * <p>接入要点（详见文档）：需在开放平台配置「应用公钥」拿到「支付宝公钥」，
 * 平台保存「应用私钥」与「支付宝公钥」；回调必须验签后再更新订单。</p>
 */
@Component
public class AlipayChannel implements PaymentChannel {

    private static final String SIGN_TYPE = "RSA2";
    private static final String FORMAT = "JSON";
    private static final String CHARSET = "UTF-8";
    private static final String GATEWAY = "https://openapi.alipay.com/gateway.do";
    private static final String GATEWAY_SANDBOX = "https://openapi.alipaydev.com/gateway.do";

    @Override
    public String channelCode() { return "alipay"; }

    private AlipayClient client(ChannelConfig cfg) {
        String gateway = cfg.isSandbox() ? GATEWAY_SANDBOX : GATEWAY;
        return new DefaultAlipayClient(
                gateway,
                cfg.getAppId(),
                cfg.get("privateKey"),
                FORMAT, CHARSET,
                cfg.get("alipayPublicKey"),
                SIGN_TYPE);
    }

    @Override
    public PayOrderResult createOrder(PayOrderRequest req, ChannelConfig cfg) {
        try {
            AlipayClient client = client(cfg);
            if ("PC".equalsIgnoreCase(req.getTradeType())) {
                AlipayTradePagePayRequest pay = new AlipayTradePagePayRequest();
                pay.setNotifyUrl(req.getNotifyUrl());
                pay.setReturnUrl(req.getReturnUrl());
                Map<String, Object> biz = new HashMap<>();
                biz.put("out_trade_no", req.getOutTradeNo());
                biz.put("total_amount", req.getAmount().toPlainString());
                biz.put("subject", req.getSubject());
                biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
                pay.setBizContent(com.alibaba.fastjson2.JSON.toJSONString(biz));
                return PayOrderResult.ofRedirect(null, client.pageExecute(pay, "GET").getBody());
            }
            // 默认扫码支付
            AlipayTradePrecreateRequest pre = new AlipayTradePrecreateRequest();
            pre.setNotifyUrl(req.getNotifyUrl());
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(req.getOutTradeNo());
            model.setTotalAmount(req.getAmount().toPlainString());
            model.setSubject(req.getSubject());
            model.setBody(req.getBody());
            pre.setBizModel(model);
            AlipayTradePrecreateResponse resp = client.execute(pre);
            if (!resp.isSuccess()) {
                throw new ChannelException(channelCode(), resp.getCode(), resp.getMsg());
            }
            // 预创建仅返回二维码与商户订单号，渠道交易号在支付/查询后获得
            return PayOrderResult.ofQr(null, resp.getQrCode());
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "SDK_ERROR", e.getMessage());
        }
    }

    @Override
    public ChannelCallback parseAndVerifyCallback(String body, java.util.Map<String, String> headers, ChannelConfig cfg) {
        try {
            Map<String, String> params = parseForm(body);
            // 1) 验签：用支付宝公钥校验回传报文真实性
            boolean ok = AlipaySignature.rsaCheckV1(params, cfg.get("alipayPublicKey"), CHARSET, SIGN_TYPE);
            if (!ok) {
                throw new ChannelException(channelCode(), "SIGN_FAIL", "回调验签失败");
            }
            // 2) 解析标准化字段
            ChannelCallback cb = new ChannelCallback();
            cb.setChannel(channelCode());
            cb.setOutTradeNo(params.get("out_trade_no"));
            cb.setTradeNo(params.get("trade_no"));
            cb.setAmount(new BigDecimal(params.getOrDefault("total_amount", "0")));
            cb.setFinishTime(params.get("gmt_payment"));
            cb.setRaw(body);
            String status = params.get("trade_status");
            cb.setResult("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status) ? "SUCCESS" : "FAIL");
            return cb;
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "CALLBACK_PARSE_ERROR", e.getMessage());
        }
    }

    @Override
    public RefundResult refund(RefundRequest req, ChannelConfig cfg) {
        try {
            AlipayClient client = client(cfg);
            AlipayTradeRefundRequest r = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(req.getOutTradeNo());
            model.setTradeNo(req.getTradeNo());
            model.setRefundAmount(req.getRefundAmount().toPlainString());
            model.setOutRequestNo(req.getOutRefundNo());
            model.setRefundReason(req.getReason());
            r.setBizModel(model);
            AlipayTradeRefundResponse resp = client.execute(r);
            if (!resp.isSuccess()) {
                throw new ChannelException(channelCode(), resp.getCode(), resp.getMsg());
            }
            RefundResult rr = new RefundResult();
            rr.setOutRefundNo(req.getOutRefundNo());
            rr.setRefundTradeNo(resp.getTradeNo());
            rr.setStatus("SUCCESS");
            rr.setRefundAmount(req.getRefundAmount());
            return rr;
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "SDK_ERROR", e.getMessage());
        }
    }

    @Override
    public QueryResult queryOrder(String outTradeNo, ChannelConfig cfg) {
        try {
            AlipayClient client = client(cfg);
            AlipayTradeQueryRequest q = new AlipayTradeQueryRequest();
            Map<String, Object> biz = new HashMap<>();
            biz.put("out_trade_no", outTradeNo);
            q.setBizContent(com.alibaba.fastjson2.JSON.toJSONString(biz));
            AlipayTradeQueryResponse resp = client.execute(q);
            QueryResult qr = new QueryResult();
            qr.setOutTradeNo(outTradeNo);
            qr.setTradeNo(resp.getTradeNo());
            qr.setRawStatus(resp.getTradeStatus());
            qr.setAmount(new BigDecimal(resp.getTotalAmount() != null ? resp.getTotalAmount() : "0"));
            qr.setStatus(mapStatus(resp.getTradeStatus()));
            qr.setPayTime(resp.getSendPayDate() != null ? resp.getSendPayDate().toString() : null);
            return qr;
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "QUERY_ERROR", e.getMessage());
        }
    }

    @Override
    public RefundResult queryRefund(String outRefundNo, ChannelConfig cfg) {
        // 支付宝退款查询属于进阶能力，此处返回处理中占位，按业务需要可扩展
        RefundResult rr = new RefundResult();
        rr.setOutRefundNo(outRefundNo);
        rr.setStatus("PROCESSING");
        return rr;
    }

    private String mapStatus(String alipayStatus) {
        if (alipayStatus == null) return PayStatus.INIT.name();
        return switch (alipayStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> PayStatus.SUCCESS.name();
            case "TRADE_CLOSED" -> PayStatus.CLOSED.name();
            case "WAIT_BUYER_PAY" -> PayStatus.PAYING.name();
            default -> PayStatus.FAILED.name();
        };
    }

    private Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                map.put(pair.substring(0, idx),
                        java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return map;
    }
}
