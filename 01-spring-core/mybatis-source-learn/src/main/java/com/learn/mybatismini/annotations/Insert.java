package com.learn.mybatismini.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 新增注解（对应 MyBatis 的 {@code @Insert}）。
 *
 * <p>若需要自增主键回填，配合 {@link Options} 使用：
 * <pre>
 *   &#64;Insert("INSERT INTO t_user(user_name) VALUES(#{userName})")
 *   &#64;Options(useGeneratedKeys = true, keyProperty = "id")
 *   int insert(User user);
 * </pre>
 *
 * @author mybatis-source-learn
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Insert {

    String value();
}
