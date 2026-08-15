package com.enterprise.erpcrm.common.result;

import java.io.Serializable;

/**
 * 统一 API 响应封装。
 * <pre>
 * 约定：
 *   - code = 200 表示成功，其余为失败；
 *   - data 承载业务数据（成功时可为 null）；
 *   - 前端统一按 { code, msg, data } 解析。
 * </pre>
 *
 * @param <T> 业务数据类型
 * @author erp-crm-system
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String msg;
    private T data;
    private final long timestamp = System.currentTimeMillis();

    private Result() {
    }

    public static <T> Result<T> success() {
        return build(RCode.SUCCESS, null);
    }

    public static <T> Result<T> success(T data) {
        return build(RCode.SUCCESS, data);
    }

    public static <T> Result<T> success(String msg, T data) {
        Result<T> r = new Result<>();
        r.code = RCode.SUCCESS.getCode();
        r.msg = msg;
        r.data = data;
        return r;
    }

    public static <T> Result<T> error(RCode rCode) {
        return build(rCode, null);
    }

    public static <T> Result<T> error(RCode rCode, String msg) {
        Result<T> r = new Result<>();
        r.code = rCode.getCode();
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    private static <T> Result<T> build(RCode rCode, T data) {
        Result<T> r = new Result<>();
        r.code = rCode.getCode();
        r.msg = rCode.getMessage();
        r.data = data;
        return r;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isSuccess() {
        return this.code == RCode.SUCCESS.getCode();
    }
}
