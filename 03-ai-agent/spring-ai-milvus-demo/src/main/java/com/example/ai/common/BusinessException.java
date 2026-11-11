package com.example.ai.common;

/** 可预期的业务异常，由 GlobalExceptionHandler 转成统一响应。 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException budgetRejected(String message) {
        return new BusinessException(ErrorCode.BUDGET_REJECTED, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }

    public int getCode() {
        return code;
    }
}
