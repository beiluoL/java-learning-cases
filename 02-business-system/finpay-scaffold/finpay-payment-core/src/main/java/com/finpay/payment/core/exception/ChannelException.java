package com.finpay.payment.core.exception;

import com.finpay.common.result.ErrorCode;

/**
 * 渠道调用异常。封装渠道返回的错误码与描述，统一上抛到业务层处理。
 */
public class ChannelException extends RuntimeException {

    private final String channel;
    private final String channelErrorCode;

    public ChannelException(String channel, String channelErrorCode, String message) {
        super("渠道[" + channel + "]错误[" + channelErrorCode + "]：" + message);
        this.channel = channel;
        this.channelErrorCode = channelErrorCode;
    }

    public ChannelException(ErrorCode e, String detail) {
        super(e.getMessage() + "：" + detail);
        this.channel = "SYSTEM";
        this.channelErrorCode = String.valueOf(e.getCode());
    }

    public String getChannel() { return channel; }
    public String getChannelErrorCode() { return channelErrorCode; }
}
