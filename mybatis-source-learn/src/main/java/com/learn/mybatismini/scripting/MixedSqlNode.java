package com.learn.mybatismini.scripting;

import java.util.List;

/**
 * 混合节点（容器节点）—— 持有一组子节点，apply 时依次调用（组合模式）。
 * （对应 MyBatis 的 {@code MixedSqlNode}）
 */
public class MixedSqlNode implements SqlNode {

    private final List<SqlNode> contents;

    public MixedSqlNode(List<SqlNode> contents) {
        this.contents = contents;
    }

    @Override
    public boolean apply(DynamicContext context) {
        boolean applied = false;
        for (SqlNode node : contents) {
            applied = node.apply(context) || applied;
        }
        return applied;
    }
}
