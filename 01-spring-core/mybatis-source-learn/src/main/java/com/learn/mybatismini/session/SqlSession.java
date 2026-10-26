package com.learn.mybatismini.session;

import com.learn.mybatismini.session.Configuration;
import java.util.List;

/**
 * SqlSession（对应 MyBatis 的 {@code SqlSession}）。
 *
 * <h3>它是 MyBatis 对外的"门面"，用户唯一直接打交道的上层 API</h3>
 * 一个 SqlSession 代表<b>一次数据库会话</b>（底层持有一条连接 + 一个 Executor）：
 * <pre>
 *   selectOne / selectList   查询
 *   insert / update / delete  写操作（默认 autoCommit=false，记得 commit）
 *   getMapper                 拿到 Mapper 接口的动态代理
 *   commit / rollback / close 事务与资源控制
 * </pre>
 *
 * <p>门面模式的价值：用户只需记 SqlSession 这几个方法，
 * 底下 SqlSessionFactory → Configuration → Executor → Handler 的复杂链路全部被屏蔽。
 *
 * <p>本接口继承 {@link AutoCloseable}，可用 try-with-resources 自动关闭资源。
 */
public interface SqlSession extends AutoCloseable {

    /** 取得全局配置（Mapper 代理内部用它解析 statementId 等） */
    Configuration getConfiguration();

    /** 查一条（多于一条会抛异常） */
    <T> T selectOne(String statement);

    <T> T selectOne(String statement, Object parameter);

    /** 查集合 */
    <E> List<E> selectList(String statement);

    <E> List<E> selectList(String statement, Object parameter);

    int insert(String statement, Object parameter);

    int update(String statement, Object parameter);

    int delete(String statement, Object parameter);

    /** 取得 Mapper 接口代理（最常用） */
    <T> T getMapper(Class<T> type);

    void commit();

    void rollback();

    void close();
}
