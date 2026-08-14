package com.learn.mybatismini.scripting;

import com.learn.mybatismini.reflection.MetaObject;
import com.learn.mybatismini.session.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态 SQL 的"临时工作台"（对应 MyBatis 的 {@code DynamicContext}）。
 *
 * <h3>它干三件事</h3>
 * <ol>
 *   <li><b>攒 SQL</b>：所有 SqlNode 通过 {@link #appendSql} 把片段拼到这里；</li>
 *   <li><b>存绑定变量</b>：{@code <foreach>} 生成的 {@code __frch_*} 临时参数放在 {@link #bind}；</li>
 *   <li><b>取参数值</b>：{@code <if test="...">} / {@code ${}} 通过 {@link #resolve} 从入参取值。</li>
 * </ol>
 *
 * <p>为什么绑定变量不塞进入参对象本身？因为入参可能是 {@code List}（根本没有属性），
 * 而且会污染用户对象。MyBatis 用一个独立的 {@code bindings} Map 来管理这些临时变量。
 */
public class DynamicContext {

    private final Configuration configuration;

    private final Object parameterObject;

    /** 临时绑定变量（含 foreach 生成的 __frch_*，以及 MyBatis 约定的 _parameter） */
    private final Map<String, Object> bindings = new HashMap<>();

    /** 拼接中的 SQL 文本 */
    private final StringBuilder sqlBuilder = new StringBuilder();

    /** 入参的元对象，用于按属性名取值（如 userName、a.b 级联） */
    private final MetaObject parameterMetaObject;

    public DynamicContext(Configuration configuration, Object parameterObject) {
        this.configuration = configuration;
        this.parameterObject = parameterObject;
        if (parameterObject == null) {
            this.parameterMetaObject = configuration.newMetaObject(new HashMap<String, Object>());
        } else {
            this.parameterMetaObject = configuration.newMetaObject(parameterObject);
        }
        this.bindings.put("_parameter", parameterObject);
    }

    /** 追加一段 SQL 片段 */
    public void appendSql(String sql) {
        sqlBuilder.append(sql).append(" ");
    }

    /** 取已拼接的 SQL 文本 */
    public String getSql() {
        return sqlBuilder.toString();
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public Object getParameterObject() {
        return parameterObject;
    }

    /** 暴露绑定变量表（供 DynamicSqlSource 把 __frch_* 拷进 BoundSql.additionalParameters） */
    public Map<String, Object> getBindings() {
        return bindings;
    }

    /** 绑定一个临时变量（如 __frch_id_0） */
    public void bind(String name, Object value) {
        bindings.put(name, value);
    }

    /**
     * 按名字解析值：先查绑定变量，再处理集合/数组特殊 key，最后走入参元对象。
     */
    public Object resolve(String name) {
        if (name == null) {
            return null;
        }
        if (bindings.containsKey(name)) {
            return bindings.get(name);
        }
        // 集合/数组特殊 key：<foreach collection="list/collection/array">
        if (parameterObject instanceof List && "list".equals(name)) {
            return parameterObject;
        }
        if (parameterObject instanceof java.util.Collection && ("list".equals(name) || "collection".equals(name))) {
            return parameterObject;
        }
        if (parameterObject != null && parameterObject.getClass().isArray() && "array".equals(name)) {
            return parameterObject;
        }
        if (parameterMetaObject.hasGetter(name)) {
            return parameterMetaObject.getValue(name);
        }
        return null;
    }
}
