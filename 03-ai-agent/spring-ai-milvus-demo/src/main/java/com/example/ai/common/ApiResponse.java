package com.example.ai.common;

/**
 * 统一响应结构。所有 Controller 只返回本类型，前端统一按 {code,message,data,traceId} 解包。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK, "success", data, TraceContext.current());
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, TraceContext.current());
    }
}
