package com.enterprise.erpcrm.common.result;

import lombok.Getter;

/**
 * 统一响应码。
 * <p>
 * 约定：{@code 200} 成功；{@code 4xx} 客户端类异常（鉴权/权限/参数）；{@code 5xx} 服务端异常；
 * {@code 1xxx} 业务自定义异常（如库存不足、流程已结束等）。
 * </p>
 *
 * @author erp-crm-system
 */
@Getter
public enum RCode {

    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(500, "系统异常，请稍后重试"),
    VALID_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不被允许"),

    BIZ_ERROR(1000, "业务处理失败"),
    DATA_SCOPE_DENIED(1001, "无权访问该范围的数据"),
    WORKFLOW_ALREADY_DONE(1002, "审批流程已结束，不可重复操作"),
    WORKFLOW_NODE_MISMATCH(1003, "当前审批节点与任务不匹配"),
    STOCK_NOT_ENOUGH(1004, "库存不足，无法出库"),
    ORDER_STATUS_INVALID(1005, "单据状态不合法，无法执行该操作");

    private final int code;
    private final String message;

    RCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
