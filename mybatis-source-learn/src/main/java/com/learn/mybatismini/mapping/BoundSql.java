package com.learn.mybatismini.mapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 绑定后的 SQL —— <b>"可以直接交给 JDBC 的最终产物"</b>。
 * （对应 MyBatis 的 {@code BoundSql}）
 *
 * <h2>它是配置世界与执行世界的分界线</h2>
 * <pre>
 *   配置世界（静态、可复用）        执行世界（动态、每次调用都不同）
 *   ┌──────────────────┐         ┌────────────────────────┐
 *   │ MappedStatement  │         │      BoundSql          │
 *   │   sqlSource      │ ──得到──▶│  sql   = 带 ? 的语句    │
 *   │   resultMap      │  参数后  │  mappings = 参数元信息   │
 *   └──────────────────┘         │  parameterObject = 实参 │
 *                                └────────────────────────┘
 * </pre>
 * MappedStatement 是<b>解析期</b>产物，全局只有一份、被所有线程共享；
 * BoundSql 是<b>调用期</b>产物，每次 {@code getBoundSql(param)} 都新建一个。
 *
 * <h3>为什么必须每次新建？</h3>
 * 因为动态 SQL 的结果依赖入参：
 * <pre>
 *   selectByCondition(userName="张")   -> WHERE user_name LIKE ?
 *   selectByCondition(age=20)          -> WHERE age >= ?
 * </pre>
 * 同一个 MappedStatement，两次调用生成的 SQL 文本和参数个数完全不同。
 * 如果把 BoundSql 缓存在 MappedStatement 上，多线程并发时就会互相踩踏 ——
 * 这是理解 MyBatis 线程安全模型的关键一环。
 *
 * <h3>additionalParameters 是什么？</h3>
 * 动态 SQL（尤其 {@code <foreach>}）会生成"入参对象里根本不存在"的临时参数：
 * <pre>
 *   &lt;foreach collection="list" item="id"&gt; #{__frch_id_0} ... #{__frch_id_1}
 * </pre>
 * 这些 {@code __frch_xxx} 的值不在 User 对象上，而是被放进 additionalParameters。
 * ParameterHandler 取值时会优先查这里 —— 这就是 foreach 能工作的秘密。
 *
 * @author mybatis-source-learn
 */
public class BoundSql {

    /** 最终 SQL 文本，{@code #{}} 已替换为 {@code ?} */
    private final String sql;

    /** 参数映射列表，顺序与 SQL 中 ? 的顺序严格一致 */
    private final List<ParameterMapping> parameterMappings;

    /** 用户传入的原始参数对象 */
    private final Object parameterObject;

    /** 动态 SQL 生成的额外参数（如 foreach 的 __frch_item_0） */
    private final Map<String, Object> additionalParameters = new HashMap<>();

    public BoundSql(String sql, List<ParameterMapping> parameterMappings, Object parameterObject) {
        this.sql = sql;
        this.parameterMappings = parameterMappings;
        this.parameterObject = parameterObject;
    }

    public String getSql() {
        return sql;
    }

    public List<ParameterMapping> getParameterMappings() {
        return parameterMappings;
    }

    public Object getParameterObject() {
        return parameterObject;
    }

    public boolean hasAdditionalParameter(String name) {
        return additionalParameters.containsKey(name);
    }

    public Object getAdditionalParameter(String name) {
        return additionalParameters.get(name);
    }

    public void setAdditionalParameter(String name, Object value) {
        additionalParameters.put(name, value);
    }

    public Map<String, Object> getAdditionalParameters() {
        return additionalParameters;
    }

    @Override
    public String toString() {
        return "BoundSql{sql='" + sql.replaceAll("\\s+", " ").trim()
                + "', params=" + parameterMappings + '}';
    }
}
