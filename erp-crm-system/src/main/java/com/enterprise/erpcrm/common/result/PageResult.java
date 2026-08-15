package com.enterprise.erpcrm.common.result;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应封装。
 * <p>与前端 Element Plus ElTable 的分页数据结构对齐：{ total, rows }。</p>
 *
 * @param <T> 行数据类型
 * @author erp-crm-system
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;
    /** 当前页数据行 */
    private List<T> rows;

    public PageResult() {
    }

    public PageResult(long total, List<T> rows) {
        this.total = total;
        this.rows = rows;
    }

    public static <T> PageResult<T> of(long total, List<T> rows) {
        return new PageResult<>(total, rows);
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRows() {
        return rows;
    }

    public void setRows(List<T> rows) {
        this.rows = rows;
    }
}
