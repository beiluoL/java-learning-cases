package com.learn.mybatismini.mapping;

/**
 * SQL 来源抽象 —— <b>"给我参数，我还你一条可执行的 SQL"</b>。
 * （对应 MyBatis 的 {@code SqlSource}）
 *
 * <h3>三个实现，对应三类 SQL</h3>
 * <pre>
 *   StaticSqlSource   已经解析完毕的静态 SQL（含 ? 与 mappings），拿来即用
 *   RawSqlSource      纯静态文本（无动态标签、无 ${}），<b>解析一次</b>后缓存成 StaticSqlSource
 *   DynamicSqlSource  含 &lt;if&gt;/&lt;foreach&gt;/${} 的动态 SQL，<b>每次调用都要重新拼</b>
 * </pre>
 *
 * <h3>性能启示（面试可答）</h3>
 * RawSqlSource 在解析配置时就把 {@code #{}} 转成了 {@code ?}，运行时零开销；
 * 而 DynamicSqlSource 每次调用都要走一遍 SqlNode 树 + 重新解析占位符。
 * 所以<b>不需要动态条件的 SQL 不要套 {@code <where>}/{@code <if>}</b>，
 * 白白损失性能。这是 MyBatis 的一个"隐藏优化点"。
 *
 * <p>这是<b>策略模式</b>：同一个"产出 SQL"的行为有三种策略，
 * MappedStatement 只持有接口，运行时多态调用。
 *
 * @author mybatis-source-learn
 */
public interface SqlSource {

    /**
     * 结合运行时参数产出 BoundSql。
     *
     * @param parameterObject 用户传入的参数（可能是实体、Map、集合或 null）
     */
    BoundSql getBoundSql(Object parameterObject);
}
