package com.learn.mybatismini.builder;

import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.session.SqlSessionFactory;
import com.learn.mybatismini.session.defaults.DefaultSqlSessionFactory;

import java.io.InputStream;
import java.io.Reader;

/**
 * SqlSessionFactory 构建器（对应 MyBatis 的 {@code SqlSessionFactoryBuilder}）。
 *
 * <h3>"读配置 → 构建 → 一次性产出工厂"的入口</h3>
 * 它用<b>建造者模式</b>把"复杂的解析过程"封装起来，调用方只需一行：
 * <pre>
 *   SqlSessionFactory factory =
 *       new SqlSessionFactoryBuilder().build(Resources.getResourceAsStream("mini-mybatis-config.xml"));
 * </pre>
 * 构建完成后，{@code SqlSessionFactoryBuilder} 自身即被丢弃 —— 它是一次性的，
 * 真正长期持有的是线程安全的 {@code SqlSessionFactory}。
 */
public class SqlSessionFactoryBuilder {

    public SqlSessionFactory build(Reader reader) {
        return build(new XMLConfigBuilder(reader).parse());
    }

    public SqlSessionFactory build(InputStream inputStream) {
        return build(new XMLConfigBuilder(inputStream).parse());
    }

    /**
     * 直接用已构建好的 Configuration 创建工厂（供测试 / 手动装配 Configuration 使用）。
     */
    public SqlSessionFactory build(Configuration configuration) {
        return new DefaultSqlSessionFactory(configuration);
    }
}
