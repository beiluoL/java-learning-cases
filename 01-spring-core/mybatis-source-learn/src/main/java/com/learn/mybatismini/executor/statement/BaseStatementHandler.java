package com.learn.mybatismini.executor.statement;

import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.type.TypeHandlerRegistry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 语句处理器基类（对应 MyBatis 的 {@code BaseStatementHandler}）。
 *
 * <p>把"与具体语句类型无关"的准备工作抽出来：
 * 在构造时通过 Configuration 创建好 {@link ParameterHandler} 和 {@link ResultSetHandler}
 * （并自动套上插件代理）。子类只需实现 {@link #instantiateStatement} 决定如何创建 Statement。
 */
public abstract class BaseStatementHandler implements StatementHandler {

    protected final Configuration configuration;

    protected final Executor executor;

    protected final MappedStatement mappedStatement;

    protected final Object parameterObject;

    protected final BoundSql boundSql;

    protected final ParameterHandler parameterHandler;

    protected final ResultSetHandler resultSetHandler;

    protected final TypeHandlerRegistry typeHandlerRegistry;

    protected BaseStatementHandler(Configuration configuration, Executor executor,
                                   MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        this.configuration = configuration;
        this.executor = executor;
        this.mappedStatement = mappedStatement;
        this.parameterObject = parameterObject;
        this.boundSql = boundSql;
        this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);
        this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, boundSql);
    }

    @Override
    public Statement prepare(Connection connection) throws SQLException {
        return instantiateStatement(connection);
    }

    /** 子类决定具体创建哪种 Statement */
    protected abstract Statement instantiateStatement(Connection connection) throws SQLException;

    @Override
    public BoundSql getBoundSql() {
        return boundSql;
    }
}
