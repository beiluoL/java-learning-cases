package com.learn.mybatismini.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明"拦截某个类的某个方法"（对应 MyBatis 的 {@code @Signature}）。
 *
 * <pre>
 *   &#64;Signature(type = Executor.class, method = "query",
 *               args = {MappedStatement.class, Object.class})
 * </pre>
 *
 * <p>{@code args} 必须写方法签名里每个参数的类型，因为 Java 方法可能重载，
 * 只有"类型列表完全匹配"才能唯一定位到被拦截的方法。
 */
@Documented
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Signature {
    Class<?> type();

    String method();

    Class<?>[] args();
}
