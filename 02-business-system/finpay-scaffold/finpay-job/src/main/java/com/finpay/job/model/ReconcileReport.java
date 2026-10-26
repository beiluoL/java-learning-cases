package com.finpay.job.model;

import lombok.Data;

import java.util.List;

/**
 * 对账报告。日终对账产出，供运营/财务复核。
 */
@Data
public class ReconcileReport {

    private String channel;
    private String date;
    private int localCount;
    private int channelCount;
    private int matchedCount;
    private List<Mismatch> mismatches;

    public int getMismatchCount() {
        return mismatches == null ? 0 : mismatches.size();
    }
}
