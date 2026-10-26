package com.enterprise.erpcrm.framework.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在写操作 Controller 方法上，由 {@code OperLogAspect} 异步记录操作行为。
 *
 * @author erp-crm-system
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 操作模块标题，如 "用户管理" */
    String title() default "";

    /** 业务类型 */
    BusinessType businessType() default BusinessType.OTHER;
}
