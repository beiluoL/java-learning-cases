package com.enterprise.erpcrm.common.exception;

import com.enterprise.erpcrm.common.result.RCode;
import lombok.Getter;

/**
 * 业务异常：由服务层显式抛出，携带业务码与可读信息，由 {@link GlobalExceptionHandler} 统一转响应。
 * <p>示例：库存不足、审批流程已结束、数据越权等。</p>
 *
 * @author erp-crm-system
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    /**
     * 最常用重载：仅给可读提示，业务码统一为 {@link RCode#BIZ_ERROR}。
     *
     * @param message 面向用户的错误提示
     */
    public BizException(String message) {
        super(message);
        this.code = RCode.BIZ_ERROR.getCode();
    }

    public BizException(RCode rCode) {
        super(rCode.getMessage());
        this.code = rCode.getCode();
    }

    public BizException(RCode rCode, String message) {
        super(message);
        this.code = rCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
