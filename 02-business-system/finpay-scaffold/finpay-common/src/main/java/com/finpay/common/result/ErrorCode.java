package com.finpay.common.result;

/**
 * 统一业务错误码枚举。金融场景错误码分组管理，便于监控与排查。
 * 规则：1xxxx 参数/校验；2xxxx 支付渠道；3xxxx 账户/交易；4xxxx 风控/权限；5xxxx 系统。
 */
public enum ErrorCode {

    SUCCESS(200, "成功"),

    PARAM_INVALID(10001, "参数校验失败"),
    SIGN_VERIFY_FAIL(10002, "签名验签失败"),
    REPEAT_REQUEST(10003, "重复请求（幂等拦截）"),

    CHANNEL_NOT_SUPPORT(20001, "不支持的支付渠道"),
    CHANNEL_ORDER_FAIL(20002, "渠道下单失败"),
    CHANNEL_REFUND_FAIL(20003, "渠道退款失败"),
    CHANNEL_QUERY_FAIL(20004, "渠道查询失败"),
    CHANNEL_CALLBACK_INVALID(20005, "渠道回调数据非法"),

    ACCOUNT_NOT_FOUND(30001, "账户不存在"),
    BALANCE_NOT_ENOUGH(30002, "账户余额不足"),
    TRANSACTION_NOT_FOUND(30003, "交易不存在"),

    FORBIDDEN(40001, "无权限访问"),
    UNAUTHORIZED(40002, "未认证或登录失效"),
    RISK_REJECTED(40003, "风控拒绝"),

    RECONCILE_MISMATCH(50001, "对账不平"),
    SYSTEM_ERROR(50000, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
