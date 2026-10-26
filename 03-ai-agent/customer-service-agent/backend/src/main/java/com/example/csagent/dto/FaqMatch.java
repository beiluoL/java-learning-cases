package com.example.csagent.dto;

import com.example.csagent.model.FaqItem;
import lombok.Data;

/** FAQ 检索命中项（含相关度分数）。 */
@Data
public class FaqMatch {
    private FaqItem item;
    private double score;

    public static FaqMatch of(FaqItem item, double score) {
        FaqMatch m = new FaqMatch();
        m.setItem(item);
        m.setScore(score);
        return m;
    }
}
