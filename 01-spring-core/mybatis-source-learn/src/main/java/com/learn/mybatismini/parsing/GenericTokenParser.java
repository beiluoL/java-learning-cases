package com.learn.mybatismini.parsing;

/**
 * 通用令牌解析器（对应 MyBatis 的 {@code GenericTokenParser}）。
 *
 * <h2>MyBatis 里最小而最关键的一个工具类</h2>
 * 它做一件很纯粹的事：在文本里找出 <b>openToken … closeToken</b> 包裹的片段，
 * 把片段内容交给 {@link TokenHandler} 处理，再用返回值替换整个令牌。
 *
 * <pre>
 *   new GenericTokenParser("#{", "}", handler)
 *       .parse("SELECT * FROM t_user WHERE id = #{id} AND age &gt; #{age}")
 *
 *   -> handler.handleToken("id")   返回 "?"
 *   -> handler.handleToken("age")  返回 "?"
 *   -> 最终结果 "SELECT * FROM t_user WHERE id = ? AND age &gt; ?"
 * </pre>
 *
 * <h3>为什么不用正则表达式？</h3>
 * 两个原因，都很实际：
 * <ol>
 *   <li><b>性能</b>：正则要编译 Pattern、做回溯匹配；这里用一次 indexOf 扫描 O(n) 搞定，
 *       而 SQL 解析是每次动态 SQL 调用都要跑的热点路径；</li>
 *   <li><b>转义支持</b>：需要支持 {@code \#{} } 表示"我就是想要字面量 #{ 不要解析"。
 *       正则处理这种转义会变得非常难读。</li>
 * </ol>
 *
 * <h3>转义规则</h3>
 * <pre>
 *   \#{id}   ->  输出字面量 "#{id}"，不做替换
 *   \}       ->  在令牌内部表示字面量 }（用于 SQL 里含 } 的极端情况）
 * </pre>
 *
 * <h3>算法要点（读代码时对照）</h3>
 * <pre>
 *   1. 找 openToken 的位置 start
 *   2. 若 openToken 前面是反斜杠 -> 转义，删掉反斜杠、原样输出 openToken，继续找下一个
 *   3. 否则从 start 之后找 closeToken（同样要处理 \} 转义）
 *   4. 取出中间内容 -> handler.handleToken(content) -> 追加返回值
 *   5. offset 后移，回到第 1 步
 * </pre>
 *
 * @author mybatis-source-learn
 */
public class GenericTokenParser {

    /** 起始定界符，如 "#{" 或 "${" */
    private final String openToken;

    /** 结束定界符，通常是 "}" */
    private final String closeToken;

    /** 令牌内容的处理策略 */
    private final TokenHandler handler;

    public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
        this.openToken = openToken;
        this.closeToken = closeToken;
        this.handler = handler;
    }

    /**
     * 解析文本，替换其中所有令牌。
     */
    public String parse(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 快速判断：没有 openToken 直接返回，避免无谓开销
        int start = text.indexOf(openToken);
        if (start == -1) {
            return text;
        }

        char[] src = text.toCharArray();
        int offset = 0;
        final StringBuilder builder = new StringBuilder();
        StringBuilder expression = null;

        while (start > -1) {
            if (start > 0 && src[start - 1] == '\\') {
                // ---------- 情况 A：openToken 被转义（\#{）----------
                // 删掉反斜杠，把 openToken 当普通文本输出
                builder.append(src, offset, start - offset - 1).append(openToken);
                offset = start + openToken.length();
            } else {
                // ---------- 情况 B：真正的令牌开始 ----------
                if (expression == null) {
                    expression = new StringBuilder();
                } else {
                    expression.setLength(0);
                }
                // 先把 openToken 之前的普通文本追加进结果
                builder.append(src, offset, start - offset);
                offset = start + openToken.length();

                // 向后寻找 closeToken
                int end = text.indexOf(closeToken, offset);
                while (end > -1) {
                    if (end > offset && src[end - 1] == '\\') {
                        // closeToken 被转义（\}），当普通字符收进表达式
                        expression.append(src, offset, end - offset - 1).append(closeToken);
                        offset = end + closeToken.length();
                        end = text.indexOf(closeToken, offset);
                    } else {
                        // 找到真正的结束位置
                        expression.append(src, offset, end - offset);
                        break;
                    }
                }

                if (end == -1) {
                    // 没有闭合的 closeToken，剩余部分原样输出（容错，不抛异常）
                    builder.append(src, start, src.length - start);
                    offset = src.length;
                } else {
                    // ★ 核心：把令牌内容交给策略处理，用返回值替换整个令牌
                    builder.append(handler.handleToken(expression.toString()));
                    offset = end + closeToken.length();
                }
            }
            start = text.indexOf(openToken, offset);
        }

        // 追加尾部剩余文本
        if (offset < src.length) {
            builder.append(src, offset, src.length - offset);
        }
        return builder.toString();
    }
}
