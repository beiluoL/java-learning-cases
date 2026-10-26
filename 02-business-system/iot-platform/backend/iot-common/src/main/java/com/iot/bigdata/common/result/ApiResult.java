package com.iot.bigdata.common.result;

import com.iot.bigdata.common.enums.ResultCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一 API 响应封装。所有 REST 接口均通过本类返回，保证前后端契约一致。
 *
 * @param <T> 业务数据类型
 */
public class ApiResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码。 */
    private int code;

    /** 提示信息。 */
    private String message;

    /** 业务数据。 */
    private T data;

    /** 响应时间戳。 */
    private LocalDateTime timestamp;

    public ApiResult() {
        this.timestamp = LocalDateTime.now();
    }

    public ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> ApiResult<T> success() {
        return success(null);
    }

    public static <T> ApiResult<T> error(ResultCode resultCode) {
        return new ApiResult<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> ApiResult<T> error(ResultCode resultCode, String message) {
        return new ApiResult<>(resultCode.getCode(), message, null);
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
