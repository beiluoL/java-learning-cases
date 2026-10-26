package com.ecommerce.learn.common.exception;

import com.ecommerce.learn.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：将所有异常收敛为统一 {@link Result} 结构
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 → 原样返回业务码与信息 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.failed(e.getCode(), e.getMessage());
    }

    /** @Valid / @Validated 参数校验失败 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException m) {
            msg = m.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + ":" + f.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else if (e instanceof BindException b) {
            msg = b.getBindingResult().getAllErrors().stream()
                    .map(o -> (o instanceof FieldError f ? f.getField() + ":" : "") + o.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        return Result.failed(CodeEnum.PARAM_ERROR.getCode(), msg);
    }

    /** 未认证（JWT 拦截器抛出） */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleUnauthorized(UnauthorizedException e) {
        return Result.error(CodeEnum.UNAUTHORIZED);
    }

    /** 无权限（角色校验失败） */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleForbidden(ForbiddenException e) {
        return Result.error(CodeEnum.FORBIDDEN);
    }

    /** 兜底系统异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleSystem(Exception e, HttpServletRequest request) {
        log.error("系统异常 uri={}", request.getRequestURI(), e);
        return Result.error(CodeEnum.SYSTEM_ERROR);
    }
}
