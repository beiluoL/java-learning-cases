package com.finpay.payment.core.api;

import com.finpay.common.exception.BizException;
import com.finpay.common.result.ErrorCode;
import com.finpay.payment.core.exception.ChannelException;
import com.finpay.payment.core.factory.ChannelManager;
import com.finpay.payment.core.model.dto.*;
import org.springframework.stereotype.Service;

/**
 * 统一支付门面。业务层唯一入口，屏蔽渠道差异：
 * 传入「渠道编码 + 商户号 + 标准化请求」，由框架路由到具体渠道实现。
 */
@Service
public class UnifiedPayService {

    private final ChannelManager channelManager;
    private final ChannelConfigProvider configProvider;

    public UnifiedPayService(ChannelManager channelManager, ChannelConfigProvider configProvider) {
        this.channelManager = channelManager;
        this.configProvider = configProvider;
    }

    public PayOrderResult pay(PayOrderRequest req) {
        ChannelConfig cfg = resolveCfg(req.getChannel(), req.getMerchantNo());
        return channelManager.get(req.getChannel()).createOrder(req, cfg);
    }

    public ChannelCallback handleCallback(String channel, String merchantNo, String body, java.util.Map<String, String> headers) {
        ChannelConfig cfg = resolveCfg(channel, merchantNo);
        return channelManager.get(channel).parseAndVerifyCallback(body, headers, cfg);
    }

    public RefundResult refund(RefundRequest req) {
        ChannelConfig cfg = resolveCfg(req.getChannel(), req.getMerchantNo());
        return channelManager.get(req.getChannel()).refund(req, cfg);
    }

    public QueryResult queryOrder(String channel, String merchantNo, String outTradeNo) {
        ChannelConfig cfg = resolveCfg(channel, merchantNo);
        return channelManager.get(channel).queryOrder(outTradeNo, cfg);
    }

    private ChannelConfig resolveCfg(String channel, String merchantNo) {
        ChannelConfig cfg = configProvider.getConfig(channel, merchantNo);
        if (cfg == null) {
            throw new BizException(ErrorCode.CHANNEL_NOT_SUPPORT, "未找到渠道配置：" + channel + ":" + merchantNo);
        }
        return cfg;
    }
}
