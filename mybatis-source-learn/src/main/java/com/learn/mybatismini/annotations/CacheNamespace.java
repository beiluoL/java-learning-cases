package com.learn.mybatismini.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为注解式 Mapper 开启二级缓存（对应 MyBatis 的 {@code @CacheNamespace}）。
 *
 * <p>等价于 XML 里的 {@code <cache eviction="LRU" size="256"/>}。
 * 加在接口上，作用范围是整个接口（一个 namespace）。
 *
 * @author mybatis-source-learn
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheNamespace {

    /** 回收策略：LRU / FIFO / PERPETUAL */
    String eviction() default "LRU";

    /** 最大缓存条目数 */
    int size() default 1024;
}
