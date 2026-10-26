package com.example.csagent.service;

import com.example.csagent.dto.Intent;
import com.example.csagent.dto.IntentResult;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图识别服务：基于关键词的规则引擎，识别三类业务意图
 * （退款 / 查询订单 / 技术支持）及未知意图。
 * 置信度 = 命中关键词数 / (命中数 + 0.6)，最高 1.0；命中 0 个即为 UNKNOWN。
 */
@Service
public class IntentService {

    private static final Map<Intent, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(Intent.REFUND, Arrays.asList(
                "退款", "退货", "退钱", "退订", "不想要", "申请退款", "拒收",
                "七天无理由", "退换", "不想买了", "钱退", "退单"));
        KEYWORDS.put(Intent.ORDER_QUERY, Arrays.asList(
                "订单", "物流", "快递", "发货", "单号", "到哪", "查订单", "查一下订单",
                "收货", "地址", "发票", "配送", "预售", "什么时候到", "货"));
        KEYWORDS.put(Intent.TECH_SUPPORT, Arrays.asList(
                "登录", "登不上", "闪退", "打不开", "崩溃", "卡死", "卡顿", "bug",
                "支付失败", "密码", "验证码", "绑定", "手机号", "安装", "教程",
                "怎么用", "设置", "功能", "黑屏", "报错", "异常"));
    }

    /** 识别用户意图。 */
    public IntentResult recognize(String text) {
        if (text == null || text.isBlank()) {
            return IntentResult.of(Intent.UNKNOWN, 0.0, "");
        }
        String lower = text.toLowerCase();

        Intent best = Intent.UNKNOWN;
        int bestHits = 0;
        StringBuilder bestMatched = new StringBuilder();

        for (Map.Entry<Intent, List<String>> e : KEYWORDS.entrySet()) {
            int hits = 0;
            StringBuilder matched = new StringBuilder();
            for (String kw : e.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    hits++;
                    if (matched.length() > 0) matched.append("、");
                    matched.append(kw);
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = e.getKey();
                bestMatched = matched;
            }
        }

        if (bestHits == 0) {
            return IntentResult.of(Intent.UNKNOWN, 0.0, "");
        }
        double confidence = Math.min(1.0, bestHits / (bestHits + 0.6));
        return IntentResult.of(best, confidence, bestMatched.toString());
    }

    /** 是否显式要求转人工。 */
    public boolean isEscalationRequest(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("转人工") || lower.contains("人工客服")
                || lower.contains("找人工") || lower.contains("真人")
                || lower.contains("人工坐席") || lower.contains("连接人工");
    }
}
