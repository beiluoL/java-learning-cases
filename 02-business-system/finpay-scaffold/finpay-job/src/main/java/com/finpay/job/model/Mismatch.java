package com.finpay.job.model;

import lombok.Data;

/**
 * 差错记录。对账发现的差异，需进入差错处理与补偿流程。
 */
@Data
public class Mismatch {

    /** 差错类型 */
    public enum Type {
        LOCAL_ONLY,     // 我方有订单，渠道无记录（可能掉单）
        CHANNEL_ONLY,   // 渠道有记录，我方无订单（可能伪造/漏单）
        AMOUNT_DIFF,    // 金额不符
        STATUS_DIFF     // 状态不符
    }

    private Type type;
    private String outTradeNo;
    private ReconciliationBill local;
    private ReconciliationBill channel;
    private String description;
}
