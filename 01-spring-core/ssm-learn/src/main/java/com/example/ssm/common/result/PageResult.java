package com.example.ssm.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页返回结构，字段命名严格对齐 Layui table 组件要求：
 * { code: 0, msg: "", count: 总条数, data: 当前页列表 }
 */
@Data
public class PageResult<T> implements Serializable {

    /** Layui 要求 0 表示成功 */
    private int code = 0;

    /** 提示信息 */
    private String msg = "";

    /** 总记录数 */
    private long count;

    /** 当前页数据 */
    private List<T> data;

    public PageResult() {
    }

    public PageResult(long count, List<T> data) {
        this.count = count;
        this.data = data;
    }
}
