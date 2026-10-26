package com.learn.mybatismini.scripting;

import com.learn.mybatismini.parsing.GenericTokenParser;
import com.learn.mybatismini.session.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@code <foreach>} 节点（对应 MyBatis 的 {@code ForEachSqlNode}）。
 *
 * <h3>这是动态 SQL 里最复杂的一个标签</h3>
 * 它要做两件事，缺一不可：
 * <ol>
 *   <li><b>拼出 IN (?, ?, ?)</b> 这种重复结构（用 open/close/separator/item）；</li>
 *   <li><b>为每个 ? 生成一个临时参数</b> {@code __frch_item_0 / __frch_item_1 ...}，
 *       并把真实值绑进 {@link DynamicContext}。这一步是 foreach 能工作的真正秘密 ——
 *       SQL 里 {@code #{item}} 会被改写成 {@code #{__frch_item_0}}，而真实元素值
 *       由 ParameterHandler 从 {@code BoundSql.additionalParameters} 里取。</li>
 * </ol>
 *
 * <pre>
 *   &lt;foreach collection="list" item="id" open="(" close=")" separator=","&gt;
 *       #{id}
 *   &lt;/foreach&gt;
 *   最终 →  ( ? , ? , ? )   且 __frch_id_0=1, __frch_id_1=2, ...
 * </pre>
 */
public class ForEachSqlNode implements SqlNode {

    /** 临时参数名前缀（与 MyBatis 命名一致，便于对照） */
    public static final String ITEM_PREFIX = "__frch_";

    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    private final String collectionExpression;

    private final SqlNode contents;

    private final String open;

    private final String close;

    private final String separator;

    private final String item;

    private final String index;

    public ForEachSqlNode(Configuration configuration, SqlNode contents,
                          String collectionExpression, String item, String index,
                          String open, String close, String separator) {
        this.contents = contents;
        this.collectionExpression = collectionExpression;
        this.item = item;
        this.index = index;
        this.open = open;
        this.close = close;
        this.separator = separator;
    }

    @Override
    public boolean apply(DynamicContext context) {
        Object collection = evaluator.evaluateObject(collectionExpression, context);
        Iterable<?> iterable = toIterable(collection);
        if (iterable == null) {
            return true;
        }
        StringBuilder sb = new StringBuilder();
        if (open != null) {
            sb.append(open).append(" ");
        }
        boolean first = true;
        int i = 0;
        for (Object element : iterable) {
            if (!first && separator != null) {
                sb.append(separator).append(" ");
            }
            first = false;
            String itemName = ITEM_PREFIX + item + "_" + i;
            String indexName = ITEM_PREFIX + index + "_" + i;
            // 把真实元素值绑成临时参数（ParameterHandler 稍后从这里取）
            context.bind(itemName, element);
            if (index != null && !index.isEmpty()) {
                context.bind(indexName, i);
            }
            // 把内部 #{item} 改写成 #{__frch_item_i}，再追加到本层 SQL
            FilteredContext fc = new FilteredContext(context, item, itemName, index, indexName);
            contents.apply(fc);
            sb.append(fc.getSql().trim());
            i++;
        }
        if (close != null) {
            sb.append(" ").append(close);
        }
        context.appendSql(sb.toString());
        return true;
    }

    /** 把集合/数组/Map 统一转成可迭代对象 */
    private Iterable<?> toIterable(Object collection) {
        if (collection == null) {
            return null;
        }
        if (collection instanceof Iterable<?> it) {
            return it;
        }
        if (collection instanceof Map<?, ?> map) {
            return new ArrayList<>(map.entrySet());
        }
        if (collection.getClass().isArray()) {
            List<Object> list = new ArrayList<>();
            int len = java.lang.reflect.Array.getLength(collection);
            for (int k = 0; k < len; k++) {
                list.add(java.lang.reflect.Array.get(collection, k));
            }
            return list;
        }
        return List.of(collection);
    }

    /**
     * 内部上下文：把 {@code #{item}} / {@code #{index}} 改写成临时参数名 {@code #{__frch_item_i}}。
     */
    private static class FilteredContext extends DynamicContext {

        private final DynamicContext delegate;

        private final String item;

        private final String itemName;

        private final String index;

        private final String indexName;

        private final StringBuilder sqlBuffer = new StringBuilder();

        FilteredContext(DynamicContext delegate, String item, String itemName, String index, String indexName) {
            super(delegate.getConfiguration(), delegate.getParameterObject());
            this.delegate = delegate;
            this.item = item;
            this.itemName = itemName;
            this.index = index;
            this.indexName = indexName;
        }

        @Override
        public void appendSql(String sql) {
            sqlBuffer.append(rewrite(sql)).append(" ");
        }

        @Override
        public Object resolve(String name) {
            return delegate.resolve(name);
        }

        @Override
        public void bind(String name, Object value) {
            delegate.bind(name, value);
        }

        public String getSql() {
            return sqlBuffer.toString();
        }

        /**
         * 把 #{item} 中的 item 替换为 __frch_item_i，<b>但必须重新包回 #{ }</b>。
         * 否则外层 DynamicSqlSource 的 SqlSourceBuilder 就再也找不到 #{ }，
         * 也就不会把它变成 ? + ParameterMapping —— 这正是之前报错
         * "Column __FRCH_ID_0 not found" 的根因。
         */
        private String rewrite(String sql) {
            GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
                int comma = content.indexOf(',');
                String property = comma < 0 ? content : content.substring(0, comma);
                String rest = comma < 0 ? "" : content.substring(comma);
                if (property.equals(item)) {
                    return "#{" + itemName + rest + "}";
                }
                if (index != null && !index.isEmpty() && property.equals(index)) {
                    return "#{" + indexName + rest + "}";
                }
                // 非 item/index 的普通 #{xxx} 也原样包回，保持 SQL 完整
                return "#{" + content + "}";
            });
            return parser.parse(sql);
        }
    }
}
