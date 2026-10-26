package com.learn.mybatismini.scripting;

/**
 * 纯静态文本节点（对应 MyBatis 的 {@code StaticTextSqlNode}）。
 *
 * <p>文本里可能含 {@code #{}}，但那是留给 {@link com.learn.mybatismini.builder.SqlSourceBuilder}
 * 在运行时统一转成 {@code ?} 的；本节点原样追加，不做任何替换。
 * 文本里<b>不</b>含 {@code ${}}（含 ${} 的用 {@link TextSqlNode}）。
 */
public class StaticTextSqlNode implements SqlNode {

    private final String text;

    public StaticTextSqlNode(String text) {
        this.text = text;
    }

    @Override
    public boolean apply(DynamicContext context) {
        context.appendSql(text);
        return true;
    }
}
