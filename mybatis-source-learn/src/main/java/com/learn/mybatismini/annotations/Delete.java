package com.learn.mybatismini.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 删除注解（对应 MyBatis 的 {@code @Delete}）。
 *
 * @author mybatis-source-learn
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Delete {

    String value();
}
