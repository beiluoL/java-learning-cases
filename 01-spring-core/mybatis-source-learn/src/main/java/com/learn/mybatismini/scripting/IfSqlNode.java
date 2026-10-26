package com.learn.mybatismini.scripting;

/**
 * {@code <if>} 条件节点（对应 MyBatis 的 {@code IfSqlNode}）。
 *
 * <p>只有 {@code test} 表达式为真时才把内部 SQL 追加进去 —— 这就是动态 SQL 的开关。
 */
public class IfSqlNode implements SqlNode {

    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    private final String test;

    private final SqlNode contents;

    public IfSqlNode(String test, SqlNode contents) {
        this.test = test;
        this.contents = contents;
    }

    @Override
    public boolean apply(DynamicContext context) {
        if (evaluator.evaluateBoolean(test, context)) {
            contents.apply(context);
            return true;
        }
        return false;
    }
}
