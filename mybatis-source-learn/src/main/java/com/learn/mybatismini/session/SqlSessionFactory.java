package com.learn.mybatismini.session;

/**
 * SqlSession 工厂（对应 MyBatis 的 {@code SqlSessionFactory}）。
 *
 * <p>由 {@code SqlSessionFactoryBuilder} 读取配置后创建，<b>全局单例、线程安全</b>。
 * 每次要用时 {@code openSession()} 拿一个全新的 SqlSession（非线程安全，用完即关）。
 */
public interface SqlSessionFactory {

    /** 开启一个 SqlSession（默认 autoCommit=false） */
    SqlSession openSession();

    /** 取全局配置（调试/扩展时有用） */
    Configuration getConfiguration();
}
