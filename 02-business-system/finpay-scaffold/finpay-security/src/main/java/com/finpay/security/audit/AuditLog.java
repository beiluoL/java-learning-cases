package com.finpay.security.audit;

import java.lang.annotation.*;

/**
 * 操作审计注解。标注在 Controller 方法上，自动记录「谁、在什么时间、对什么资源、做了什么操作」。
 * 审计数据经由 {@link AuditLogRecorder} 落库，满足等保/金融监管对完整操作审计日志的要求。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 操作类型，如 QUERY / PAY / REFUND / RECONCILE / CONFIG */
    String action();

    /** 被审计的资源类型，如 PAYMENT_ORDER / CHANNEL_CONFIG / ACCOUNT */
    String resource() default "";

    /** 是否记录请求参数（敏感接口建议 false） */
    boolean logParams() default true;
}
