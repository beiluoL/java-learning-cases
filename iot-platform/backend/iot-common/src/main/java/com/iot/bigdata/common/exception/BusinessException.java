package com.iot.bigdata.common.exception;

import com.iot.bigdata.common.enums.ResultCode;
import lombok.Getter;

/**
 * 业务异常。用于承载可预期的、需要向前端明确提示的错误（如设备不存在、阈值越界等）。
 *
 * <p>禁止用异常做流程控制；本异常仅表达“业务条件不满足”。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码（对应 {@link ResultCode} 或自定义码）。 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
