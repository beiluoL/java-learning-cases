package com.learn.mybatismini.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 参数命名注解（对应 MyBatis 的 {@code @Param}）。
 *
 * <h3>为什么多参数方法一定要写 @Param？</h3>
 * 先看没写会发生什么：
 * <pre>
 *   int countByAgeRange(int min, int max);
 * </pre>
 * Java 编译时<b>默认会丢弃形参名</b>，反射只能拿到 {@code arg0 / arg1}。
 * 于是 MyBatis 只能把参数包成：
 * <pre>
 *   Map{arg0=18, arg1=30, param1=18, param2=30}
 * </pre>
 * 此时 SQL 里写 {@code #{min}} 就会报经典错误：
 * <blockquote>Parameter 'min' not found. Available parameters are [arg0, arg1, param1, param2]</blockquote>
 *
 * <p>两种解法：
 * <ol>
 *   <li>写 {@code @Param("min")}（推荐，显式且稳定）；</li>
 *   <li>编译时加 {@code -parameters} 参数保留形参名
 *       （本项目 pom.xml 已开启，所以不写 @Param 也能用真实参数名，
 *       但换个构建环境就可能失效，<b>生产代码仍建议写 @Param</b>）。</li>
 * </ol>
 *
 * <p>具体包装逻辑见 {@code binding/ParamNameResolver}。
 *
 * @author mybatis-source-learn
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {

    /** 参数在 SQL 中使用的名字 */
    String value();
}
