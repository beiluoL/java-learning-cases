package com.example.mq.common.exception;

import com.example.mq.common.api.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常：可预期、可恢复，不需要打印完整堆栈（区别于系统异常）。
 * 由 {@code GlobalExceptionHandler} 统一转换为 {@code Result.fail(...)}。
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 业务异常不需要堆栈，省掉 fillInStackTrace 的开销（高频路径上有意义） */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
