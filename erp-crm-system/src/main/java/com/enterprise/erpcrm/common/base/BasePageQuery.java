package com.enterprise.erpcrm.common.base;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询基类：前端列表查询统一继承，承载页码、页大小与排序。
 * <p>配合 MyBatis-Plus {@link Page} 使用：{@code toPage()} 直接转为分页对象。</p>
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class BasePageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页，默认第 1 页（对齐 Element Plus 分页组件） */
    private Integer pageNum = 1;
    /** 每页大小，默认 10 */
    private Integer pageSize = 10;
    /** 排序字段，如 "create_time desc"；为空则由 Mapper 决定默认排序 */
    private String orderBy;

    /**
     * 转为 MyBatis-Plus 分页对象。pageNum 下限保护，避免 0/负数导致分页异常。
     */
    public <T> IPage<T> toPage() {
        int num = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        return new Page<>(num, size);
    }
}
