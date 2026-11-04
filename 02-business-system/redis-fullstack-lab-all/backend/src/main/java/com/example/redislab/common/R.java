package com.example.redislab.common;

import java.io.Serializable;

/**
 * 统一响应结构 { code, msg, data }。
 * 约定：code=0 表示成功，非 0 表示业务/系统错误；msg 给人看的提示；data 业务数据。
 */
public class R<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public R() {}

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 0;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    public static <T> R<T> fail(String msg) {
        return fail(500, msg);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
