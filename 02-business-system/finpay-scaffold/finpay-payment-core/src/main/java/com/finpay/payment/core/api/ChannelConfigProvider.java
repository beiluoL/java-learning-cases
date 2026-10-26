package com.finpay.payment.core.api;

import com.finpay.payment.core.model.dto.ChannelConfig;

/**
 * 渠道配置提供方。业务系统实现该接口，从数据库（渠道配置表）按「商户 + 渠道」加载运行参数。
 * 框架通过该接口解耦「配置存储」与「渠道调用」。
 */
public interface ChannelConfigProvider {

    /**
     * 获取指定商户在某渠道的配置。
     * @param channel    渠道编码
     * @param merchantNo 商户号
     * @return 渠道配置；若未配置返回 null
     */
    ChannelConfig getConfig(String channel, String merchantNo);

    /** 列出所有已启用的渠道编码 */
    java.util.List<String> listChannels();
}
