package com.learn.mybatismini.scripting;

import com.learn.mybatismini.session.Configuration;

import java.util.List;

/**
 * 裁剪节点（对应 MyBatis 的 {@code TrimSqlNode}）。
 *
 * <h3>{@code <where>} 和 {@code <set>} 都是它的特化</h3>
 * 它把内部 SQL 先攒到一个<b>临时缓冲</b>里，apply 结束后再做"裁剪"：
 * <ul>
 *   <li>去掉<b>开头的</b>指定前缀（如 {@code AND }、{@code OR }、{@code ,}）；</li>
 *   <li>去掉<b>结尾的</b>指定后缀（如 {@code ,}）；</li>
 *   <li>再在前面补上 {@code prefix}（如 WHERE / SET）。</li>
 * </ul>
 * 这样无论内部条件是否生成，"WHERE AND ..." 这种语法错误的 SQL 都不会出现。
 *
 * <p>用临时缓冲再统一裁剪，是 MyBatis 一个很巧妙的设计 —— 否则你无法知道
 * 内部条件到底有没有生成，也就无从决定要不要去掉前缀 AND。
 */
public class TrimSqlNode implements SqlNode {

    private final SqlNode contents;

    private final String prefix;

    private final List<String> prefixesToOverride;

    private final List<String> suffixesToOverride;

    public TrimSqlNode(Configuration configuration, SqlNode contents, String prefix,
                       List<String> prefixesToOverride, List<String> suffixesToOverride) {
        this.contents = contents;
        this.prefix = prefix;
        this.prefixesToOverride = prefixesToOverride;
        this.suffixesToOverride = suffixesToOverride;
    }

    @Override
    public boolean apply(DynamicContext context) {
        // 内部内容先写进"临时缓冲"，不立即进最终 SQL
        FilteredDynamicContext filtered = new FilteredDynamicContext(context);
        contents.apply(filtered);
        filtered.applyAll();
        return true;
    }

    /**
     * 临时缓冲上下文：收集内部 SQL，最后由 applyAll 统一裁剪并写回外层。
     */
    private class FilteredDynamicContext extends DynamicContext {

        private final DynamicContext delegate;

        private final StringBuilder sqlBuffer = new StringBuilder();

        FilteredDynamicContext(DynamicContext delegate) {
            super(delegate.getConfiguration(), delegate.getParameterObject());
            this.delegate = delegate;
        }

        @Override
        public void appendSql(String sql) {
            sqlBuffer.append(sql).append(" ");
        }

        @Override
        public Object resolve(String name) {
            return delegate.resolve(name);
        }

        @Override
        public void bind(String name, Object value) {
            delegate.bind(name, value);
        }

        /** 裁剪 + 回填到外层上下文 */
        void applyAll() {
            String sql = sqlBuffer.toString().trim();
            if (sql.isEmpty()) {
                return;
            }
            // 去掉开头的多余前缀（如 AND / OR / ,）
            if (prefixesToOverride != null) {
                for (String toRemove : prefixesToOverride) {
                    if (startsWithIgnoreCase(sql, toRemove)) {
                        sql = sql.substring(toRemove.length()).trim();
                        break;
                    }
                }
            }
            // 去掉结尾的多余后缀（如 ,）
            if (suffixesToOverride != null) {
                for (String toRemove : suffixesToOverride) {
                    if (endsWithIgnoreCase(sql, toRemove)) {
                        sql = sql.substring(0, sql.length() - toRemove.length()).trim();
                    }
                }
            }
            if (prefix != null && !prefix.isEmpty()) {
                delegate.appendSql(prefix + " " + sql);
            } else {
                delegate.appendSql(sql);
            }
        }

        private boolean startsWithIgnoreCase(String s, String prefix) {
            return s.length() >= prefix.length()
                    && s.substring(0, prefix.length()).equalsIgnoreCase(prefix);
        }

        private boolean endsWithIgnoreCase(String s, String suffix) {
            return s.length() >= suffix.length()
                    && s.substring(s.length() - suffix.length()).equalsIgnoreCase(suffix);
        }
    }
}
