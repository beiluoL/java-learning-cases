package com.learn.mybatismini.scripting;

import java.util.List;

/**
 * {@code <choose>/<when>/<otherwise>} 节点（相当于 switch-case）。
 * （对应 MyBatis 的 {@code ChooseSqlNode}）
 *
 * <p>按顺序判断每个 {@code <when>} 的 test，命中第一个就输出并结束；
 * 都不命中则输出 {@code <otherwise>}。注意：只输出<b>一个</b>分支，这正是与多个
 * 并列 {@code <if>}（可能输出多个）的关键区别。
 */
public class ChooseSqlNode implements SqlNode {

    /** 一个 when 分支：条件 + 内容 */
    public static class When {
        final String test;
        final SqlNode contents;

        public When(String test, SqlNode contents) {
            this.test = test;
            this.contents = contents;
        }
    }

    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    private final List<When> whens;

    private final SqlNode otherwise;

    public ChooseSqlNode(List<When> whens, SqlNode otherwise) {
        this.whens = whens;
        this.otherwise = otherwise;
    }

    @Override
    public boolean apply(DynamicContext context) {
        for (When when : whens) {
            if (evaluator.evaluateBoolean(when.test, context)) {
                when.contents.apply(context);
                return true;
            }
        }
        if (otherwise != null) {
            otherwise.apply(context);
            return true;
        }
        return false;
    }
}
