package com.learn.mybatismini.executor.statement;

import java.sql.PreparedStatement;

/**
 * 参数处理器（对应 MyBatis 的 {@code ParameterHandler}）。
 *
 * <p>唯一职责：把 {@code #{...}} 对应的真实值，按 JDBC 下标设进 PreparedStatement。
 * 具体"Java 类型 -> JDBC 类型"的转换由 {@code TypeHandler} 完成。
 */
public interface ParameterHandler {

    /** 取原始参数对象 */
    Object getParameterObject();

    /** 把参数依次 set 进 PreparedStatement 的 ? 占位符 */
    void setParameters(PreparedStatement ps) throws java.sql.SQLException;
}
