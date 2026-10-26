package com.finpay.job.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 对账单行。无论来自「渠道账单」还是「本地订单」，统一抽象为相同结构以便比对。
 */
@Data
public class ReconciliationBill {

    /** 商户订单号（对账主键） */
    private String outTradeNo;
    /** 渠道交易号 */
    private String tradeNo;
    /** 金额（元） */
    private BigDecimal amount;
    /** 支付状态：SUCCESS / FAIL */
    private String status;
    /** 完成时间 */
    private String finishTime;
}
