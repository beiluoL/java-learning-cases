package com.example.videostream.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理：将业务异常、参数校验异常、未捕获异常统一转换为 {@link Result}。
 * 避免异常信息直接泄漏给客户端，同时保证日志可追溯。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：已知、可预期，直接返回对应错误码，不打 ERROR 堆栈（仅 WARN 记录上下文）。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex, HttpServletRequest request) {
        log.warn("BizException uri={} code={} msg={}", request.getRequestURI(), ex.getCode(), ex.getBizMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ex.getCode(), ex.getBizMessage()));
    }

    /** 参数校验失败（@Valid / @Validated）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ((FieldError) fe).getField() + ":" + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("ParamInvalid {}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ErrorCode.PARAM_INVALID, detail));
    }

    /** 兜底：未知异常，返回系统错误，记录完整堆栈便于排查。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("UnhandledException uri={}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.SYSTEM_ERROR));
    }
}
