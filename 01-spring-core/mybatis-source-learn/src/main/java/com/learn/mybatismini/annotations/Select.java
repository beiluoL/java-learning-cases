package com.learn.mybatismini.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 查询注解（对应 MyBatis 的 {@code @Select}）。
 *
 * <h3>@Retention(RUNTIME) 是关键，必须理解</h3>
 * Java 注解有三种保留策略：
 * <pre>
 *   SOURCE  只存在于源码，编译后丢弃（如 @Override、Lombok 的注解）
 *   CLASS   写进 class 文件，但运行时反射<b>拿不到</b>（默认值！）
 *   RUNTIME 写进 class 文件且运行时可通过反射读取  ← 框架注解必须用它
 * </pre>
 * MyBatis 要在启动时用 {@code method.getAnnotation(Select.class)} 读取 SQL，
 * 如果保留策略不是 RUNTIME，反射会返回 null，整个注解方式就失效了。
 * <b>自己写框架注解时最容易踩的坑就是忘写 @Retention(RUNTIME)。</b>
 *
 * <h3>用法</h3>
 * <pre>
 *   &#64;Select("SELECT id, user_name FROM t_user WHERE id = #{id}")
 *   User selectById(Long id);
 * </pre>
 * 注解里的 SQL 同样支持 {@code #{}} 占位符 —— 因为注解解析器
 * （MapperAnnotationBuilder）最终也是把字符串丢给 SqlSourceBuilder 处理，
 * 和 XML 走的是同一条解析链路。
 *
 * @author mybatis-source-learn
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Select {

    /** SQL 语句 */
    String value();
}
