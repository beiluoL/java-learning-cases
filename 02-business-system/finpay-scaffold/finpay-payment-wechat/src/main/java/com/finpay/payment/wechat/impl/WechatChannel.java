package com.finpay.payment.wechat.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.finpay.payment.core.api.PaymentChannel;
import com.finpay.payment.core.exception.ChannelException;
import com.finpay.payment.core.model.dto.*;
import com.finpay.payment.core.model.enums.PayStatus;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.AutoUpdateCertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信支付 V3 渠道实现。基于 wechatpay-apache-httpclient，覆盖：Native 扫码下单、回调验签+解密、退款、查询。
 *
 * <p>核心差异（与支付宝）：</p>
 * <ul>
 *   <li>下单用 HTTPS JSON + 请求签名（商户私钥），响应自动验签（平台证书）。</li>
 *   <li>回调签名放在 HTTP Header（Wechatpay-Signature 等），报文用 APIv3Key(AES-GCM) 加密，需先解密再解析。</li>
 *   <li>金额单位为「分」，与支付宝「元」不同，转换时注意。</li>
 * </ul>
 */
@Component
public class WechatChannel implements PaymentChannel {

    private static final String V3 = "https://api.mch.weixin.qq.com";
    private final Map<String, CloseableHttpClient> clientCache = new ConcurrentHashMap<>();

    @Override
    public String channelCode() { return "wechat"; }

    private CloseableHttpClient client(ChannelConfig cfg) {
        return clientCache.computeIfAbsent(cfg.getMerchantNo(), k -> {
            try {
                String mchid = cfg.getMerchantNo();
                String serial = cfg.get("serialNo");
                PrivateKey key = PemUtil.loadPrivateKey(cfg.get("privateKey"));
                String apiV3 = cfg.get("apiV3Key");
                var credentials = new WechatPay2Credentials(mchid, new PrivateKeySigner(serial, key));
                var verifier = new AutoUpdateCertificatesVerifier(credentials, apiV3.getBytes(StandardCharsets.UTF_8));
                return WechatPayHttpClientBuilder.create()
                        .withMerchant(mchid, serial, key)
                        .withValidator(new WechatPay2Validator(verifier))
                        .build();
            } catch (Exception e) {
                throw new ChannelException(channelCode(), "INIT_CLIENT", e.getMessage());
            }
        });
    }

    @Override
    public PayOrderResult createOrder(PayOrderRequest req, ChannelConfig cfg) {
        try {
            JSONObject biz = new JSONObject();
            biz.set("mchid", cfg.getMerchantNo());
            biz.set("appid", cfg.getAppId());
            biz.set("description", req.getSubject());
            biz.set("out_trade_no", req.getOutTradeNo());
            biz.set("notify_url", req.getNotifyUrl());
            JSONObject amount = new JSONObject();
            amount.set("total", req.getAmount().multiply(new BigDecimal("100")).setScale(0, RoundingMode.DOWN).intValue());
            amount.set("currency", "CNY");
            biz.set("amount", amount);

            HttpPost post = new HttpPost(V3 + "/v3/pay/transactions/native");
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(biz.toString(), StandardCharsets.UTF_8));
            try (CloseableHttpResponse resp = client(cfg).execute(post)) {
                String respStr = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (resp.getStatusLine().getStatusCode() != 200) {
                    throw new ChannelException(channelCode(), "CREATE_FAIL", respStr);
                }
                JSONObject json = JSONUtil.parseObj(respStr);
                return PayOrderResult.ofQr(null, json.getStr("code_url"));
            }
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "SDK_ERROR", e.getMessage());
        }
    }

    @Override
    public ChannelCallback parseAndVerifyCallback(String body, Map<String, String> headers, ChannelConfig cfg) {
        try {
            // 1) 验签：header 中的签名 + 构造待签名串 = timestamp\nnonce\nbody
            String serial = headers.get("Wechatpay-Serial") != null ? headers.get("Wechatpay-Serial")
                    : headers.get("wechatpay-serial");
            String sig = headers.get("Wechatpay-Signature") != null ? headers.get("Wechatpay-Signature")
                    : headers.get("wechatpay-signature");
            String timestamp = headers.get("Wechatpay-Timestamp") != null ? headers.get("Wechatpay-Timestamp")
                    : headers.get("wechatpay-timestamp");
            String nonce = headers.get("Wechatpay-Nonce") != null ? headers.get("Wechatpay-Nonce")
                    : headers.get("wechatpay-nonce");
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";
            // 使用平台证书验签（AutoUpdateCertificatesVerifier 内部已含最新证书）
            var verifier = ((AutoUpdateCertificatesVerifier) verifierOf(cfg));
            if (!verifier.verify(serial, message.getBytes(StandardCharsets.UTF_8), sig)) {
                throw new ChannelException(channelCode(), "SIGN_FAIL", "回调验签失败");
            }
            // 2) 解密 resource（微信 V3：AES-256-GCM，密钥=APIv3Key，nonce + associatedData 参与运算，密文尾部含 16 字节 tag）
            JSONObject root = JSONUtil.parseObj(body);
            JSONObject resource = root.getJSONObject("resource");
            String plain = decryptGcm(cfg.get("apiV3Key"),
                    resource.getStr("associated_data", ""),
                    resource.getStr("nonce"),
                    resource.getStr("ciphertext"));
            JSONObject data = JSONUtil.parseObj(plain);
            ChannelCallback cb = new ChannelCallback();
            cb.setChannel(channelCode());
            cb.setOutTradeNo(data.getStr("out_trade_no"));
            cb.setTradeNo(data.getStr("transaction_id"));
            cb.setAmount(new BigDecimal(data.getJSONObject("amount").getInt("total")).divide(new BigDecimal("100")));
            cb.setFinishTime(data.getStr("success_time"));
            cb.setRaw(body);
            cb.setResult("SUCCESS".equals(data.getStr("trade_state")) ? "SUCCESS" : "FAIL");
            return cb;
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "CALLBACK_PARSE_ERROR", e.getMessage());
        }
    }

    /** 微信 V3 回调报文 AES-256-GCM 解密（APIv3Key 为 32 字节） */
    private String decryptGcm(String apiV3Key, String associatedData, String nonce, String ciphertextBase64) {
        try {
            byte[] key = apiV3Key.getBytes(StandardCharsets.UTF_8);
            byte[] nonceBytes = nonce.getBytes(StandardCharsets.UTF_8);
            byte[] aad = (associatedData == null ? "" : associatedData).getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = Base64.getDecoder().decode(ciphertextBase64);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.GCMParameterSpec(128, nonceBytes));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "DECRYPT_ERROR", e.getMessage());
        }
    }

    private Object verifierOf(ChannelConfig cfg) {
        // 复用 client 内部 verifier：这里重建一个用于验签（生产可缓存 verifier）
        try {
            String mchid = cfg.getMerchantNo();
            PrivateKey key = PemUtil.loadPrivateKey(cfg.get("privateKey"));
            var credentials = new WechatPay2Credentials(mchid, new PrivateKeySigner(cfg.get("serialNo"), key));
            return new AutoUpdateCertificatesVerifier(credentials, cfg.get("apiV3Key").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "VERIFIER_ERR", e.getMessage());
        }
    }

    @Override
    public RefundResult refund(RefundRequest req, ChannelConfig cfg) {
        try {
            JSONObject biz = new JSONObject();
            biz.set("out_trade_no", req.getOutTradeNo());
            biz.set("out_refund_no", req.getOutRefundNo());
            biz.set("reason", req.getReason());
            JSONObject amount = new JSONObject();
            amount.set("refund", req.getRefundAmount().multiply(new BigDecimal("100")).setScale(0, RoundingMode.DOWN).intValue());
            amount.set("total", req.getTotalAmount().multiply(new BigDecimal("100")).setScale(0, RoundingMode.DOWN).intValue());
            amount.set("currency", "CNY");
            biz.set("amount", amount);

            HttpPost post = new HttpPost(V3 + "/v3/refund/domestic/refunds");
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(biz.toString(), StandardCharsets.UTF_8));
            try (CloseableHttpResponse resp = client(cfg).execute(post)) {
                String respStr = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (resp.getStatusLine().getStatusCode() != 200) {
                    throw new ChannelException(channelCode(), "REFUND_FAIL", respStr);
                }
                JSONObject json = JSONUtil.parseObj(respStr);
                RefundResult rr = new RefundResult();
                rr.setOutRefundNo(req.getOutRefundNo());
                rr.setRefundTradeNo(json.getStr("refund_id"));
                rr.setStatus("PROCESSING");
                rr.setRefundAmount(req.getRefundAmount());
                return rr;
            }
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "SDK_ERROR", e.getMessage());
        }
    }

    @Override
    public QueryResult queryOrder(String outTradeNo, ChannelConfig cfg) {
        try {
            String url = V3 + "/v3/pay/transactions/out-trade-no/" + outTradeNo + "?mchid=" + cfg.getMerchantNo();
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse resp = client(cfg).execute(get)) {
                String respStr = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (resp.getStatusLine().getStatusCode() != 200) {
                    throw new ChannelException(channelCode(), "QUERY_FAIL", respStr);
                }
                JSONObject json = JSONUtil.parseObj(respStr);
                QueryResult qr = new QueryResult();
                qr.setOutTradeNo(outTradeNo);
                qr.setTradeNo(json.getStr("transaction_id"));
                qr.setRawStatus(json.getStr("trade_state"));
                qr.setAmount(new BigDecimal(json.getJSONObject("amount").getInt("total")).divide(new BigDecimal("100")));
                qr.setStatus(mapStatus(json.getStr("trade_state")));
                qr.setPayTime(json.getStr("success_time"));
                return qr;
            }
        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException(channelCode(), "QUERY_ERROR", e.getMessage());
        }
    }

    @Override
    public RefundResult queryRefund(String outRefundNo, ChannelConfig cfg) {
        RefundResult rr = new RefundResult();
        rr.setOutRefundNo(outRefundNo);
        rr.setStatus("PROCESSING");
        return rr;
    }

    private String mapStatus(String wxStatus) {
        if (wxStatus == null) return PayStatus.INIT.name();
        return switch (wxStatus) {
            case "SUCCESS" -> PayStatus.SUCCESS.name();
            case "CLOSED" -> PayStatus.CLOSED.name();
            case "USERPAYING" -> PayStatus.PAYING.name();
            default -> PayStatus.FAILED.name();
        };
    }
}
