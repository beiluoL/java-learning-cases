package com.learn.mybatismini.exceptions;

/**
 * 框架异常体系的根异常。
 *
 * <h3>为什么用 RuntimeException（非受检异常）？</h3>
 * JDBC 原生 API 抛的 {@link java.sql.SQLException} 是受检异常，导致业务代码里到处是
 * try-catch 或 throws，噪音极大。MyBatis 的做法是：
 * <pre>
 *   捕获 SQLException  ->  包装成 PersistenceException（RuntimeException）向上抛
 * </pre>
 * 这样业务层不再被迫处理它，同时又保留了原始异常（cause）便于排查。
 * 这是"异常转译（Exception Translation）"模式，Spring 的 DataAccessException 同理。
 *
 * <p>本项目的异常继承体系：
 * <pre>
 *   PersistenceException（根）
 *     ├── ReflectionException   反射/属性访问出错
 *     ├── BuilderException      配置解析（XML/注解）出错
 *     ├── BindingException      Mapper 接口绑定出错（找不到 statement 等）
 *     └── ExecutorException     执行 SQL 阶段出错
 * </pre>
 *
 * @author mybatis-source-learn
 */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceException() {
        super();
    }

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(Throwable cause) {
        super(cause);
    }
}
