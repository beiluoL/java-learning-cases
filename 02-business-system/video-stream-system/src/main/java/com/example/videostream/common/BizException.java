package com.example.videostream.common;

import lombok.Getter;

/**
 * 业务异常：所有可预期的业务错误统一抛出此异常，由 {@link GlobalExceptionHandler} 统一转换为 {@link Result}。
 * <p>
 * 用法示例：throw new BizException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
 * 或携带自定义消息：throw new BizException(ErrorCode.SYSTEM_ERROR, "与对象存储通信超时");
 * </p>
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;
    private final String bizMessage;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.bizMessage = errorCode.getMessage();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
        this.bizMessage = detail;
    }
}
