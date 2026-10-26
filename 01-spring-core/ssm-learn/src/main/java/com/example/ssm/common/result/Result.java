package com.example.ssm.common.result;

import com.example.ssm.common.constant.Constants;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 返回结构（非表格类接口使用）。
 * 约定：code=0 成功（与 Layui 表格、PageResult 保持统一），其余为失败；前端根据 code 判断。
 */
@Data
public class Result<T> implements Serializable {

    /** 状态码 */
    private int code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = Constants.CODE_SUCCESS;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(String msg) {
        Result<T> r = new Result<>();
        r.code = Constants.CODE_ERROR;
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
