package com.learn.mybatismini.scripting;

import com.learn.mybatismini.session.Configuration;

import java.util.List;

/**
 * {@code <where>} 节点：{@link TrimSqlNode} 的特化。
 *
 * <p>自动添加 WHERE 关键字，并去掉其后的多余 {@code AND } / {@code OR } 前缀。
 * 对应 XML 里的：
 * <pre>
 *   &lt;where&gt;
 *     &lt;if test="..."&gt;AND user_name = #{userName}&lt;/if&gt;
 *   &lt;/where&gt;
 * </pre>
 */
public class WhereSqlNode extends TrimSqlNode {

    /** 要被去掉的"多余前缀"列表（含空格与各种换行/制表符） */
    private static final List<String> PREFIXES_TO_OVERRIDE = List.of(
            "AND ", "OR ", "AND\n", "OR\n", "AND\r", "OR\r", "AND\t", "OR\t");

    public WhereSqlNode(Configuration configuration, SqlNode contents) {
        super(configuration, contents, "WHERE", PREFIXES_TO_OVERRIDE, null);
    }
}
