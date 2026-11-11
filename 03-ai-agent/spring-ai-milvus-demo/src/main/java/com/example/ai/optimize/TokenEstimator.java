package com.example.ai.optimize;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 Token 估算器，<b>仅用于优化前后的相对比较</b>。
 *
 * <p>严禁用它替代供应商 Usage 做账单：供应商分词器与计费口径各不相同，
 * 任何由本类算出的数字都必须标注 {@code ESTIMATED}。
 *
 * <p>启发式规则（宁可高估，避免低估导致预算击穿）：
 * <ul>
 *   <li>CJK 字符：1 token / 字；</li>
 *   <li>ASCII 单词：1.3 token / 词；</li>
 *   <li>其余符号/空白：0.5 token / 字符。</li>
 * </ul>
 */
@Component
public class TokenEstimator {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff\\u3400-\\u4dbf\\u3040-\\u30ff\\uac00-\\ud7af]");
    private static final Pattern ASCII_WORD = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern OTHER = Pattern.compile("[^\\s\\u4e00-\\u9fff\\u3400-\\u4dbf\\u3040-\\u30ff\\uac00-\\ud7afA-Za-z0-9_]");

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjk = count(CJK, text);
        int words = count(ASCII_WORD, text);
        int other = count(OTHER, text);
        long tokens = cjk + Math.round(words * 1.3d) + Math.round(other * 0.5d);
        return (int) Math.max(tokens, 1);
    }

    /** 多条文本合计，用于 Prompt 组装后的整体估算。 */
    public int estimate(String... parts) {
        int sum = 0;
        for (String p : parts) {
            sum += estimate(p);
        }
        return sum;
    }

    private int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
