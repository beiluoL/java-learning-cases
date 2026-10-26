package com.finpay.payment.core.model.dto;

import lombok.Data;

/**
 * 统一下单返回。不同渠道的「拉起支付」形态不同，由 channelData 承载差异：
 * - NATIVE（扫码）：qrCode 二维码内容
 * - PC 网站：redirectUrl 跳转地址 或 formHtml 自动提交表单
 * - JSAPI/APP：channelData 含 prepayId / paySign 等，交由前端调起
 */
@Data
public class PayOrderResult {

    /** 渠道侧交易号 */
    private String tradeNo;

    /** 二维码内容（扫码支付） */
    private String qrCode;

    /** 跳转支付页 URL（网站支付） */
    private String redirectUrl;

    /** 自动提交表单 HTML（网站支付） */
    private String formHtml;

    /** 渠道透传数据（JSAPI/APP 调起所需） */
    private java.util.Map<String, String> channelData;

    public static PayOrderResult ofQr(String tradeNo, String qrCode) {
        PayOrderResult r = new PayOrderResult();
        r.tradeNo = tradeNo;
        r.qrCode = qrCode;
        return r;
    }

    public static PayOrderResult ofRedirect(String tradeNo, String redirectUrl) {
        PayOrderResult r = new PayOrderResult();
        r.tradeNo = tradeNo;
        r.redirectUrl = redirectUrl;
        return r;
    }
}
