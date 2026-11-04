package com.example.redislab.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常兜底：把散落的异常统一收口成 R 结构，并设置正确的 HTTP 状态码。
 * 注意：R 里的 code 只是业务码；真正的 HTTP 状态码由 @ResponseStatus 决定，
 * 二者保持一致，前端/压测脚本才能通过 HTTP 状态码正确区分「被限流(429)」与「成功(200)」。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 被限流，返回 HTTP 429 */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public R<Void> handleRateLimit(RateLimitException e) {
        return R.fail(429, e.getMessage());
    }

    /** 参数校验/业务断言失败，返回 HTTP 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegal(IllegalArgumentException e) {
        return R.fail(HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    /** 其它未捕获异常，返回 HTTP 500 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handle(Exception e) {
        return R.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
    }
}
