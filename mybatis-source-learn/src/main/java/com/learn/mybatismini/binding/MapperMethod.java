package com.learn.mybatismini.binding;

import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.mapping.SqlCommandType;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.session.SqlSession;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Mapper 方法（对应 MyBatis 的 {@code MapperMethod}）。
 *
 * <h3>把"接口方法"翻译成"SqlSession 的调用"</h3>
 * 它预先解析出两样东西缓存起来：
 * <ul>
 *   <li>{@link SqlCommand}：statementId + SQL 类型（SELECT/INSERT/...）；</li>
 *   <li>{@link MethodSignature}：返回类型、是否返回集合/void。</li>
 * </ul>
 * 运行时 {@link #execute} 按 SQL 类型选择 {@code session.select/insert/update/delete}，
 * 把实参交给 {@link ParamNameResolver} 包装成 SQL 参数。
 */
public class MapperMethod {

    private final SqlCommand command;

    private final MethodSignature signature;

    private final ParamNameResolver paramNameResolver;

    public MapperMethod(Class<?> mapperInterface, Method method, Configuration configuration) {
        String statementId = mapperInterface.getName() + "." + method.getName();
        MappedStatement ms = configuration.getMappedStatement(statementId);
        this.command = new SqlCommand(statementId, ms.getSqlCommandType());
        this.signature = new MethodSignature(method);
        this.paramNameResolver = new ParamNameResolver(method);
    }

    public Object execute(SqlSession sqlSession, Object[] args) {
        Object param = paramNameResolver.resolveNamedParams(args);
        switch (command.type) {
            case INSERT:
                return rowCountResult(sqlSession.insert(command.name, param));
            case UPDATE:
                return rowCountResult(sqlSession.update(command.name, param));
            case DELETE:
                return rowCountResult(sqlSession.delete(command.name, param));
            case SELECT:
                if (signature.returnsVoid) {
                    // 返回 void：执行但不取结果（真实 MyBatis 还支持 ResultHandler 回调，此处略）
                    sqlSession.selectList(command.name, param);
                    return null;
                }
                if (signature.returnsMany) {
                    return sqlSession.selectList(command.name, param);
                }
                return sqlSession.selectOne(command.name, param);
            default:
                throw new PersistenceException("未知 SQL 类型：" + command.type + "（Mapper 方法：" + command.name + "）");
        }
    }

    /** 把受影响的行数转成方法声明的返回类型 */
    private Object rowCountResult(int rows) {
        Class<?> ret = signature.returnType;
        if (ret == void.class || ret == Void.class) {
            return null;
        }
        if (ret == Integer.class || ret == int.class) {
            return rows;
        }
        if (ret == Long.class || ret == long.class) {
            return (long) rows;
        }
        if (ret == Boolean.class || ret == boolean.class) {
            return rows > 0;
        }
        return rows;
    }

    /** SQL 命令：statementId + 类型 */
    public static class SqlCommand {
        public final String name;
        public final SqlCommandType type;

        public SqlCommand(String name, SqlCommandType type) {
            this.name = name;
            this.type = type;
        }
    }

    /** 方法签名信息 */
    public static class MethodSignature {
        public final Class<?> returnType;
        public final boolean returnsVoid;
        public final boolean returnsMany;

        public MethodSignature(Method method) {
            this.returnType = method.getReturnType();
            this.returnsVoid = returnType == void.class;
            this.returnsMany = List.class.isAssignableFrom(returnType) || returnType.isArray();
        }
    }
}
