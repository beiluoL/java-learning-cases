package com.learn.mybatismini.scripting;

import com.learn.mybatismini.builder.SqlSourceBuilder;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.SqlSource;
import com.learn.mybatismini.session.Configuration;

import java.util.Map;

/**
 * 动态 SQL 源（对应 MyBatis 的 {@code DynamicSqlSource}）。
 *
 * <h3>"每次调用都重新拼 SQL" —— 动态标签 / ${} 的代价</h3>
 * <pre>
 *   getBoundSql(parameter) {
 *       1. DynamicContext 工作台就位
 *       2. 根 SqlNode 递归 apply，按入参裁剪/拼接出最终 SQL 文本
 *       3. SqlSourceBuilder 把 #{...} 转成 ? 并生成 ParameterMapping
 *       4. 把 foreach 的 __frch_* 临时参数拷进 BoundSql.additionalParameters
 *       5. 返回全新的 BoundSql
 *   }
 * </pre>
 * 因为 SQL 依赖入参，所以无法缓存，每次都得走一遍 SqlNode 树。
 */
public class DynamicSqlSource implements SqlSource {

    private final Configuration configuration;

    private final SqlNode rootSqlNode;

    public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
        this.configuration = configuration;
        this.rootSqlNode = rootSqlNode;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        DynamicContext context = new DynamicContext(configuration, parameterObject);
        rootSqlNode.apply(context);

        SqlSourceBuilder builder = new SqlSourceBuilder(configuration);
        SqlSource staticSql = builder.parse(context.getSql(), null, parameterObject);
        BoundSql boundSql = staticSql.getBoundSql(parameterObject);

        // 把 foreach 生成的临时参数搬进 BoundSql，ParameterHandler 稍后从这里取值
        for (Map.Entry<String, Object> entry : context.getBindings().entrySet()) {
            boundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
        }
        return boundSql;
    }
}
