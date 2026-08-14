package com.learn.mybatismini.scripting;

import com.learn.mybatismini.parsing.GenericTokenParser;

/**
 * 含 {@code ${}} 的文本节点（对应 MyBatis 的 {@code TextSqlNode}）。
 *
 * <h3>${} 是"字符串直接替换"，不走 PreparedStatement 占位符</h3>
 * 这里用 {@link GenericTokenParser} 把 {@code ${orderByColumn}} 求值为真实字符串后内联进 SQL。
 * 因为没有 {@code ?}，它会被原样拼进语句 —— 所以来自用户输入的 ${} 有 SQL 注入风险，
 * 只能用于列名、表名、排序字段等"结构部分"。
 */
public class TextSqlNode implements SqlNode {

    private final String text;

    /** 是否含 ${}（含即为动态节点） */
    private final boolean dynamic;

    public TextSqlNode(String text) {
        this.text = text;
        this.dynamic = text.contains("${");
    }

    public boolean isDynamic() {
        return dynamic;
    }

    @Override
    public boolean apply(DynamicContext context) {
        GenericTokenParser parser = new GenericTokenParser("${", "}", content -> {
            String value = new ExpressionEvaluator().evaluateString(content, context);
            return value == null ? "" : value;
        });
        context.appendSql(parser.parse(text));
        return true;
    }
}
