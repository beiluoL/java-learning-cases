package com.example.mq.config;

import com.example.mq.common.api.Result;
import com.example.mq.common.api.ResultCode;
import com.example.mq.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把各类异常收敛成统一的 {@link Result}。
 * <p>
 * 生产规范：
 * <ul>
 *   <li>业务异常记 warn（可预期，不需要堆栈），系统异常记 error（必须带堆栈）；</li>
 *   <li>不要把堆栈/sql 暴露给前端，用固定文案 + 服务端日志定位；</li>
 *   <li>HTTP 统一 200，业务码区分 —— 避免前端要同时处理两层错误码。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("[BIZ-ERROR] code={} message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidation(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + defaultMessage(fieldError))
                .collect(Collectors.joining("; "));
        log.warn("[VALID-ERROR] {}", message);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message.isEmpty() ? "参数不合法" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        log.warn("[VALID-ERROR] {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public Result<Void> handleBadRequest(Exception e) {
        log.warn("[BAD-REQUEST] {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "请求参数格式不正确：" + e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("[DB-DUPLICATE] {}", e.getMessage());
        return Result.fail(ResultCode.REPEAT_SUBMIT);
    }

    @ExceptionHandler(AmqpException.class)
    public Result<Void> handleAmqp(AmqpException e) {
        log.error("[MQ-ERROR] RabbitMQ 操作失败", e);
        return Result.fail(ResultCode.SERVICE_UNAVAILABLE, "消息服务暂不可用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("[SYS-ERROR] 未捕获异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }

    private String defaultMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() == null ? "不合法" : fieldError.getDefaultMessage();
    }
}
