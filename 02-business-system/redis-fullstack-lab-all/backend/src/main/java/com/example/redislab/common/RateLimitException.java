package com.example.redislab.common;

/**
 * 限流异常：被滑动窗口拦截时抛出，由全局异常处理器映射为 HTTP 429。
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
