package com.enterprise.erpcrm.framework.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 功能权限校验注解：标注在 Controller 方法上，要求当前用户拥有指定权限标识。
 * <p>由 {@code PermissionAspect} 拦截校验；超级管理员（含 super-admin-role）自动放行。</p>
 *
 * @author erp-crm-system
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermissions {

    /** 权限标识集合，如 {"system:user:list"} */
    String[] value();

    /** 逻辑关系：默认 AND（全部满足） */
    Logical logical() default Logical.AND;
}
