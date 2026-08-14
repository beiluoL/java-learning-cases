package com.learn.mybatismini.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为拦截器，并声明它要拦截哪些方法（对应 MyBatis 的 {@code @Intercepts}）。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Intercepts {
    Signature[] value();
}
