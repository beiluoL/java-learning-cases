package com.learn.mybatismini.scripting;

import com.learn.mybatismini.session.Configuration;

import java.util.List;

/**
 * {@code <set>} 节点：{@link TrimSqlNode} 的特化。
 *
 * <p>自动添加 SET 关键字，并去掉开头/结尾多余的 {@code ,}（动态列拼接时极易多出逗号）。
 */
public class SetSqlNode extends TrimSqlNode {

    private static final List<String> COMMAS = List.of(",");

    public SetSqlNode(Configuration configuration, SqlNode contents) {
        super(configuration, contents, "SET", COMMAS, COMMAS);
    }
}
