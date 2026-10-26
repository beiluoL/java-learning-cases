package com.learn.mybatismini.scripting;

import com.learn.mybatismini.mapping.BoundSql;

/**
 * SQL 节点（对应 MyBatis 的 {@code SqlNode}）。
 *
 * <h3>动态 SQL 的本质：一棵"会自我裁剪的语法树"</h3>
 * 每个标签（{@code <if>}/{@code <where>}/{@code <foreach>}/纯文本）都是一个 SqlNode。
 * 调用 {@link #apply} 时，节点根据自身逻辑把 SQL 片段追加到 {@link DynamicContext}：
 * <pre>
 *   MixedSqlNode（根）
 *     ├─ StaticTextSqlNode  "SELECT * FROM t_user"
 *     ├─ WhereSqlNode
 *     │    └─ IfSqlNode(test="userName != null")  ── 条件成立才追加 AND user_name = ?
 *     └─ ...
 * </pre>
 * 这是<b>组合模式（Composite）</b>：叶子节点和容器节点实现同一个接口，
 * 对调用方（XMLScriptBuilder）而言没有区别，递归 apply 即可拼出最终 SQL。
 */
public interface SqlNode {

    /**
     * 把本节点代表的 SQL 片段追加到上下文。返回是否产生了内容。
     */
    boolean apply(DynamicContext context);
}
