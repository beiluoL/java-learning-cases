package com.learn.mybatismini.builder;

import com.learn.mybatismini.session.Configuration;

/**
 * 构建器基类（对应 MyBatis 的 {@code BaseBuilder}）。
 *
 * <p>所有 XML 构建器（XMLConfigBuilder / XMLMapperBuilder / MapperAnnotationBuilder）
 * 都持有同一个 {@link Configuration}，并提供"按别名解析类型"的通用能力。
 */
public abstract class BaseBuilder {

    protected final Configuration configuration;

    protected BaseBuilder(Configuration configuration) {
        this.configuration = configuration;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    /** 按别名 / 全限定名解析类型（失败返回 null） */
    protected Class<?> resolveClass(String alias) {
        if (alias == null || alias.isEmpty()) {
            return null;
        }
        return configuration.getTypeAliasRegistry().resolveAlias(alias);
    }
}
