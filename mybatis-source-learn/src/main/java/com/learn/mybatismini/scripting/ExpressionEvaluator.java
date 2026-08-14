package com.learn.mybatismini.scripting;

import com.learn.mybatismini.exceptions.PersistenceException;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态 SQL 表达式求值器（对应 MyBatis 的 {@code ExpressionEvaluator}，官方用 OGNL）。
 *
 * <h3>为什么自己写一个迷你求值器？</h3>
 * MyBatis 官方依赖 OGNL 第三方库。为了"零依赖、看得懂"，这里手写一个递归下降的小解析器，
 * 覆盖动态 SQL 里 99% 的场景：
 * <pre>
 *   userName != null
 *   age &gt;= 18
 *   status == 1
 *   userName != '' and age != null
 *   id != null or userName != null
 * </pre>
 *
 * <h3>支持的语法</h3>
 * <ul>
 *   <li>操作数：标识符（从入参取值）、字符串字面量（'x' 或 "x"）、数字、true/false、null；</li>
 *   <li>比较：== != &lt; &gt; &lt;= &gt;=</li>
 *   <li>逻辑：and / or / && / || / !（优先级：! &gt; 比较 &gt; and &gt; or）；</li>
 *   <li>括号分组：(...)。</li>
 * </ul>
 */
public class ExpressionEvaluator {

    /**
     * 求布尔值（供 {@code <if test="...">} 用）。
     */
    public boolean evaluateBoolean(String expression, DynamicContext context) {
        Object value = evaluate(expression, context);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        // 非空字符串 / 非空对象 视为 true（兼容 MyBatis 的"真值"语义）
        if (value instanceof String s) {
            return !s.isEmpty();
        }
        return true;
    }

    /**
     * 求字符串（供 {@code ${...}} 替换用）。
     */
    public String evaluateString(String expression, DynamicContext context) {
        Object value = evaluate(expression, context);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 求任意对象（供 {@code <foreach collection="...">} 取集合用）。
     */
    public Object evaluateObject(String expression, DynamicContext context) {
        return evaluate(expression, context);
    }

    // ==================================================================
    // 迷你递归下降解析器
    // ==================================================================

    private Object evaluate(String expression, DynamicContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        List<Token> tokens = tokenize(expression);
        Parser parser = new Parser(tokens, context);
        Object result = parser.parseOr();
        if (parser.pos != tokens.size()) {
            throw new PersistenceException("无法解析动态 SQL 表达式：" + expression);
        }
        return result;
    }

    // ------------------- 词法分析 -------------------

    private enum Tk {
        AND, OR, EQ, NE, LT, GT, LE, GE, NOT, LPAREN, RPAREN, IDENT, STRING, NUMBER, BOOL, NULL
    }

    private static final class Token {
        final Tk type;
        final String text;
        final Object literal;

        Token(Tk type, String text, Object literal) {
            this.type = type;
            this.text = text;
            this.literal = literal;
        }
    }

    private List<Token> tokenize(String src) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '=' && i + 1 < n && src.charAt(i + 1) == '=') {
                tokens.add(new Token(Tk.EQ, "==", null));
                i += 2;
            } else if (c == '!' && i + 1 < n && src.charAt(i + 1) == '=') {
                tokens.add(new Token(Tk.NE, "!=", null));
                i += 2;
            } else if (c == '<' && i + 1 < n && src.charAt(i + 1) == '=') {
                tokens.add(new Token(Tk.LE, "<=", null));
                i += 2;
            } else if (c == '>' && i + 1 < n && src.charAt(i + 1) == '=') {
                tokens.add(new Token(Tk.GE, ">=", null));
                i += 2;
            } else if (c == '<') {
                tokens.add(new Token(Tk.LT, "<", null));
                i++;
            } else if (c == '>') {
                tokens.add(new Token(Tk.GT, ">", null));
                i++;
            } else if (c == '(') {
                tokens.add(new Token(Tk.LPAREN, "(", null));
                i++;
            } else if (c == ')') {
                tokens.add(new Token(Tk.RPAREN, ")", null));
                i++;
            } else if (c == '!') {
                tokens.add(new Token(Tk.NOT, "!", null));
                i++;
            } else if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < n && src.charAt(i) != quote) {
                    sb.append(src.charAt(i++));
                }
                i++; // 跳过结尾引号
                tokens.add(new Token(Tk.STRING, sb.toString(), sb.toString()));
            } else if (Character.isDigit(c) || (c == '-' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))) {
                int start = i;
                i++;
                while (i < n && (Character.isDigit(src.charAt(i)) || src.charAt(i) == '.')) {
                    i++;
                }
                String num = src.substring(start, i);
                Object val = num.contains(".") ? Double.parseDouble(num) : Long.parseLong(num);
                tokens.add(new Token(Tk.NUMBER, num, val));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                i++;
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_' || src.charAt(i) == '.')) {
                    i++;
                }
                String word = src.substring(start, i);
                if ("and".equalsIgnoreCase(word) || "&&".equals(word)) {
                    tokens.add(new Token(Tk.AND, word, null));
                } else if ("or".equalsIgnoreCase(word) || "||".equals(word)) {
                    tokens.add(new Token(Tk.OR, word, null));
                } else if ("true".equalsIgnoreCase(word)) {
                    tokens.add(new Token(Tk.BOOL, word, Boolean.TRUE));
                } else if ("false".equalsIgnoreCase(word)) {
                    tokens.add(new Token(Tk.BOOL, word, Boolean.FALSE));
                } else if ("null".equalsIgnoreCase(word)) {
                    tokens.add(new Token(Tk.NULL, word, null));
                } else {
                    tokens.add(new Token(Tk.IDENT, word, null));
                }
            } else {
                throw new PersistenceException("动态 SQL 表达式含无法识别的字符：'" + c + "' 在 " + src);
            }
        }
        return tokens;
    }

    // ------------------- 语法分析（递归下降） -------------------

    private static final class Parser {
        private final List<Token> tokens;
        private final DynamicContext context;
        private int pos;

        Parser(List<Token> tokens, DynamicContext context) {
            this.tokens = tokens;
            this.context = context;
        }

        Object parseOr() {
            Object left = parseAnd();
            while (peek(Tk.OR)) {
                next();
                Object right = parseAnd();
                left = toBool(left) || toBool(right);
            }
            return left;
        }

        Object parseAnd() {
            Object left = parseCmp();
            while (peek(Tk.AND)) {
                next();
                Object right = parseCmp();
                left = toBool(left) && toBool(right);
            }
            return left;
        }

        Object parseCmp() {
            if (peek(Tk.NOT)) {
                next();
                return !(toBool(parseCmp()));
            }
            Object left = parsePrimary();
            if (peek(Tk.EQ, Tk.NE, Tk.LT, Tk.GT, Tk.LE, Tk.GE)) {
                Tk op = tokens.get(pos).type;
                next();
                Object right = parsePrimary();
                return compare(op, left, right);
            }
            return left;
        }

        Object parsePrimary() {
            Token t = tokens.get(pos);
            if (t.type == Tk.LPAREN) {
                next();
                Object v = parseOr();
                if (!peek(Tk.RPAREN)) {
                    throw new PersistenceException("表达式缺少右括号");
                }
                next();
                return v;
            }
            if (t.type == Tk.STRING || t.type == Tk.NUMBER || t.type == Tk.BOOL || t.type == Tk.NULL) {
                next();
                return t.literal;
            }
            if (t.type == Tk.IDENT) {
                next();
                return context.resolve(t.text);
            }
            throw new PersistenceException("表达式语法错误，位置：" + pos);
        }

        // ------------------- 工具 -------------------

        private boolean peek(Tk... types) {
            if (pos >= tokens.size()) {
                return false;
            }
            Tk cur = tokens.get(pos).type;
            for (Tk type : types) {
                if (cur == type) {
                    return true;
                }
            }
            return false;
        }

        private void next() {
            pos++;
        }

        private static boolean toBool(Object o) {
            if (o == null) {
                return false;
            }
            if (o instanceof Boolean b) {
                return b;
            }
            if (o instanceof String s) {
                return !s.isEmpty();
            }
            if (o instanceof Number num) {
                return num.doubleValue() != 0;
            }
            return true;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Object compare(Tk op, Object left, Object right) {
            // 处理 null：== / != 对 null 有明确定义
            boolean ln = left == null, rn = right == null;
            switch (op) {
                case EQ:
                    return (ln || rn) ? ln && rn : Objects_equal(left, right);
                case NE:
                    return !(ln || rn) ? !Objects_equal(left, right) : !(ln && rn);
                default:
                    break;
            }
            // 比较运算：有一方为 null 直接返回 false（无法比较）
            if (ln || rn) {
                return false;
            }
            int cmp;
            if (left instanceof Number a && right instanceof Number b) {
                cmp = Double.compare(a.doubleValue(), b.doubleValue());
            } else if (left instanceof Comparable && right instanceof Comparable) {
                cmp = ((Comparable) left).compareTo(right);
            } else {
                throw new PersistenceException("无法对 " + left + " 与 " + right + " 做大小比较");
            }
            return switch (op) {
                case LT -> cmp < 0;
                case GT -> cmp > 0;
                case LE -> cmp <= 0;
                case GE -> cmp >= 0;
                default -> false;
            };
        }

        private static boolean Objects_equal(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
        }
    }
}
