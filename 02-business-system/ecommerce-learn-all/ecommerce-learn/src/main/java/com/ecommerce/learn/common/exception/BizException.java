package com.ecommerce.learn.common.exception;

import lombok.Getter;

/**
 * 业务异常：在 Service 层主动抛出，由全局异常处理器统一转换
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = CodeEnum.BIZ_ERROR.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(CodeEnum codeEnum) {
        super(codeEnum.getMessage());
        this.code = codeEnum.getCode();
    }

    public BizException(CodeEnum codeEnum, String detail) {
        super(detail);
        this.code = codeEnum.getCode();
    }
}
