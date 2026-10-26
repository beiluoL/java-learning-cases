package com.ecommerce.learn.common.exception;

import lombok.Getter;

/**
 * 业务异常枚举：统一定义接口返回码与文案
 */
@Getter
public enum CodeEnum {

    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不被允许"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),
    BIZ_ERROR(1000, "业务处理失败");

    private final int code;
    private final String message;

    CodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
