package com.example.ssm.common.exception;

/**
 * 业务异常：用于主动抛出可预期的业务错误（如“用户不存在”）。
 * 由 GlobalExceptionHandler 统一捕获并转换为 Result 返回前端。
 */
public class BusinessException extends RuntimeException {

    private int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
