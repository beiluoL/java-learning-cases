package com.iot.bigdata.common.enums;

import lombok.Getter;

/**
 * 统一返回码枚举（与 HTTP 语义对齐，便于前端与网关统一处理）。
 */
@Getter
public enum ResultCode {

    /** 成功。 */
    SUCCESS(200, "操作成功"),
    /** 参数校验失败。 */
    PARAM_ERROR(400, "请求参数错误"),
    /** 未认证。 */
    UNAUTHORIZED(401, "未登录或登录已过期"),
    /** 无权限。 */
    FORBIDDEN(403, "没有访问权限"),
    /** 资源不存在。 */
    NOT_FOUND(404, "请求的资源不存在"),
    /** 业务异常（如设备离线、阈值越界等可预期错误）。 */
    BUSINESS_ERROR(409, "业务处理失败"),
    /** 系统内部错误。 */
    SYSTEM_ERROR(500, "系统内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
