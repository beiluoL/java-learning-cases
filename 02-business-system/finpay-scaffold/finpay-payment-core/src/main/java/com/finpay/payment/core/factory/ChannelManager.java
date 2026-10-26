package com.finpay.payment.core.factory;

import com.finpay.common.exception.BizException;
import com.finpay.common.result.ErrorCode;
import com.finpay.payment.core.api.PaymentChannel;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付渠道管理器（工厂 + 注册表）。Spring 自动收集所有 {@link PaymentChannel} Bean，
 * 业务层按渠道编码取用。新增渠道 = 新增一个 PaymentChannel 实现 + 在数据库登记配置。
 */
@Component
public class ChannelManager {

    private final Map<String, PaymentChannel> registry = new ConcurrentHashMap<>();
    private final List<PaymentChannel> channels;

    public ChannelManager(List<PaymentChannel> channels) {
        this.channels = channels;
    }

    @PostConstruct
    public void init() {
        for (PaymentChannel c : channels) {
            registry.put(c.channelCode().toLowerCase(), c);
        }
    }

    public PaymentChannel get(String channelCode) {
        PaymentChannel c = registry.get(channelCode.toLowerCase());
        if (c == null) {
            throw new BizException(ErrorCode.CHANNEL_NOT_SUPPORT, channelCode);
        }
        return c;
    }

    public boolean supports(String channelCode) {
        return registry.containsKey(channelCode.toLowerCase());
    }
}
