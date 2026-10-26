package com.ecommerce.learn.common.exception;

/**
 * 未认证异常（由 JWT 拦截器抛出，被 {@link GlobalExceptionHandler} 转换为 401）
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
