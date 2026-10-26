package com.iot.bigdata.common.result;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页查询结果封装。注意：分页数据放在 {@code records} 字段（约定优于配置，便于前端统一取值）。
 *
 * @param <T> 行数据类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据。 */
    private List<T> records = new ArrayList<>();

    /** 总记录数。 */
    private long total;

    /** 当前页码（从 1 开始）。 */
    private long pageNum;

    /** 每页大小。 */
    private long pageSize;

    /** 总页数。 */
    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.records = records == null ? new ArrayList<>() : records;
        result.total = total;
        result.pageNum = pageNum;
        result.pageSize = pageSize;
        result.pages = pageSize > 0 ? (long) Math.ceil((double) total / pageSize) : 0;
        return result;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPageNum() {
        return pageNum;
    }

    public void setPageNum(long pageNum) {
        this.pageNum = pageNum;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }
}
