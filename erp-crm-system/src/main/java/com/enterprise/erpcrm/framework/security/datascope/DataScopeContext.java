package com.enterprise.erpcrm.framework.security.datascope;

/**
 * 数据权限上下文：ThreadLocal 承载切面计算出的 SQL 过滤片段（如 " AND d.dept_id IN (1,2,3)"）。
 * <p>Service 方法体内通过 {@link #get()} 取出并作为 {@code dataScope} 参数传给 Mapper。</p>
 *
 * @author erp-crm-system
 */
public final class DataScopeContext {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(String fragment) {
        CONTEXT.set(fragment);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
