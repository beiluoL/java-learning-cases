package com.example.csagent.dto;

import lombok.Data;

/** 意图识别结果。 */
@Data
public class IntentResult {
    private Intent intent;
    private double confidence;
    /** 命中的关键词（用于解释/调试） */
    private String matchedKeywords;

    public static IntentResult of(Intent intent, double confidence, String matchedKeywords) {
        IntentResult r = new IntentResult();
        r.setIntent(intent);
        r.setConfidence(confidence);
        r.setMatchedKeywords(matchedKeywords);
        return r;
    }
}
