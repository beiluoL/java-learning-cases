package com.ecommerce.learn.common.exception;

/**
 * 无权限异常（角色校验失败，被 {@link GlobalExceptionHandler} 转换为 403）
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
