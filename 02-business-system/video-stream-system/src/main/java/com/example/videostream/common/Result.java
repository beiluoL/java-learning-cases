package com.example.videostream.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包装。前端/客户端约定以 code=="00000" 表示成功。
 *
 * @param <T> 业务数据载荷类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错误码，成功为 00000 */
    private String code;
    /** 提示消息 */
    private String message;
    /** 业务数据 */
    private T data;
    /** 服务器时间戳（毫秒） */
    private Long timestamp;

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.message = ErrorCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        Result<T> r = new Result<>();
        r.code = errorCode.getCode();
        r.message = errorCode.getMessage();
        return r;
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String detail) {
        Result<T> r = new Result<>();
        r.code = errorCode.getCode();
        r.message = detail;
        return r;
    }

    /** 直接以原始 code + message 构造失败响应（用于异常处理器透传真实错误码）。 */
    public static <T> Result<T> fail(String code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
