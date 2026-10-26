package com.ecommerce.learn.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页查询结果封装
 *
 * @param <T> 行数据类型
 */
@Data
public class PageResult<T> implements Serializable {

    /** 当前页数据 */
    private List<T> records;
    /** 总记录数 */
    private long total;
    /** 每页大小 */
    private long size;
    /** 当前页码（从 1 开始） */
    private long current;
    /** 总页数 */
    private long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long size, long current, long pages) {
        this.records = records;
        this.total = total;
        this.size = size;
        this.current = current;
        this.pages = pages;
    }
}
