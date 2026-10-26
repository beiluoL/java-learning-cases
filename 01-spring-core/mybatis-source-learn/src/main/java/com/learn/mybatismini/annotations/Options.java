package com.learn.mybatismini.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 语句选项注解（对应 MyBatis 的 {@code @Options}）。
 *
 * <p>用于在注解方式下补充 XML 里那些属性：
 * <pre>
 *   &#64;Options(useGeneratedKeys = true, keyProperty = "id")   自增主键回填
 *   &#64;Options(useCache = false)                              该查询不走二级缓存
 *   &#64;Options(flushCache = true)                             执行后清空缓存
 * </pre>
 *
 * @author mybatis-source-learn
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Options {

    /** 是否使用数据库自增主键并回填 */
    boolean useGeneratedKeys() default false;

    /** 自增主键回填到入参对象的哪个属性 */
    String keyProperty() default "";

    /** 是否使用二级缓存（仅对查询有意义） */
    boolean useCache() default true;

    /** 执行后是否清空二级缓存 */
    boolean flushCache() default false;
}
