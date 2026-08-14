package com.learn.mybatismini.reflection.property;

import java.util.Iterator;

/**
 * 属性表达式分词器（对应 MyBatis 的 {@code PropertyTokenizer}）。
 *
 * <h3>它解决什么问题？</h3>
 * MyBatis 允许在 {@code #{}} 和 resultMap 的 property 里写<b>复杂属性表达式</b>：
 * <pre>
 *   user.address.city        级联属性
 *   orders[0].amount         索引访问（List / 数组）
 *   headers[token]           Map 的 key 访问
 * </pre>
 * MetaObject 要按这个表达式一层层取值/设值，就需要先把它切成"当前段 + 剩余段"。
 *
 * <h3>切分示例：{@code "orders[0].amount"}</h3>
 * <pre>
 *   name      = "orders"            当前段的属性名（不含索引）
 *   indexedName = "orders[0]"       当前段的完整写法（含索引）
 *   index     = "0"                 索引/key
 *   children  = "amount"            剩下的表达式，交给下一层继续切
 * </pre>
 * 这是一个典型的<b>迭代器模式</b>：本类同时实现 Iterator 与 Iterable，
 * {@link #next()} 返回代表"剩余部分"的新分词器，形成链式递归下降解析。
 *
 * @author mybatis-source-learn
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer>, Iterable<PropertyTokenizer> {

    /** 当前段属性名，如 orders */
    private String name;
    /** 当前段完整名（含索引），如 orders[0] */
    private final String indexedName;
    /** 索引或 Map 的 key，如 0 / token；没有则为 null */
    private String index;
    /** 剩余表达式，如 amount；没有则为 null */
    private final String children;

    public PropertyTokenizer(String fullname) {
        // 第一步：按第一个 '.' 切成 [当前段, 剩余段]
        int delim = fullname.indexOf('.');
        if (delim > -1) {
            name = fullname.substring(0, delim);
            children = fullname.substring(delim + 1);
        } else {
            name = fullname;
            children = null;
        }
        indexedName = name;

        // 第二步：当前段里若有 '['，再切出索引
        delim = name.indexOf('[');
        if (delim > -1) {
            index = name.substring(delim + 1, name.length() - 1);
            name = name.substring(0, delim);
        }
    }

    public String getName() {
        return name;
    }

    public String getIndex() {
        return index;
    }

    public String getIndexedName() {
        return indexedName;
    }

    public String getChildren() {
        return children;
    }

    /** 是否还有下一层（即表达式里还有 '.' 之后的内容） */
    @Override
    public boolean hasNext() {
        return children != null;
    }

    /** 取下一层分词器 */
    @Override
    public PropertyTokenizer next() {
        return new PropertyTokenizer(children);
    }

    @Override
    public Iterator<PropertyTokenizer> iterator() {
        return this;
    }

    @Override
    public String toString() {
        return "PropertyTokenizer{name='" + name + "', index='" + index
                + "', children='" + children + "'}";
    }
}
