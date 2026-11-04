package com.example.redislab.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常兜底：把散落的异常统一收口成 R 结构，前端永远拿到 {code,msg,data}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 被限流，返回 429 */
    @ExceptionHandler(RateLimitException.class)
    public R<Void> handleRateLimit(RateLimitException e) {
        return R.fail(429, e.getMessage());
    }

    /** 参数校验/业务断言失败，返回 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegal(IllegalArgumentException e) {
        return R.fail(HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    /** 其它未捕获异常，返回 500 */
    @ExceptionHandler(Exception.class)
    public R<Void> handle(Exception e) {
        return R.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
    }
}
