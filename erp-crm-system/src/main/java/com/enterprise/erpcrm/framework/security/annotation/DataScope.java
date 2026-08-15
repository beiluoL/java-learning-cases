package com.enterprise.erpcrm.framework.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解：标注在「列表查询 Service 方法」上，由 {@code DataScopeAspect} 自动注入数据过滤条件。
 * <p>切面依据当前用户的数据权限范围（全部/自定义/本部门/本部门及以下/仅本人）计算 SQL 片段，
 * 存入 {@code DataScopeContext}，Service 方法取出后作为 {@code dataScope} 参数透传给 Mapper。</p>
 *
 * @author erp-crm-system
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 部门字段所属表别名，如 "d" */
    String deptAlias() default "d";

    /** 用户字段（仅本人场景）所属表别名，如 "u" */
    String userAlias() default "u";

    /** 部门列名 */
    String deptColumn() default "dept_id";

    /** 用户列名（create_by/user_id 等） */
    String userColumn() default "create_by";
}
