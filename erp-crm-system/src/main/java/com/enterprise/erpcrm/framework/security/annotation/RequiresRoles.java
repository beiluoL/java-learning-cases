package com.enterprise.erpcrm.framework.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解：要求当前用户拥有指定角色标识。超级管理员自动放行。
 *
 * @author erp-crm-system
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRoles {

    /** 角色标识集合，如 {"SALES_MANAGER"} */
    String[] value();

    Logical logical() default Logical.AND;
}
