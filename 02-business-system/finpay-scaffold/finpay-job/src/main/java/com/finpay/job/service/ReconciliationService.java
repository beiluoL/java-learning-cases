package com.finpay.job.service;

import com.finpay.job.model.ReconcileReport;

/**
 * 对账服务。比对「本地订单」与「渠道账单」，产出差错明细。
 */
public interface ReconciliationService {

    /**
     * 执行指定渠道、指定日期的日终对账。
     * @param channel 渠道编码
     * @param date    yyyy-MM-dd
     */
    ReconcileReport reconcile(String channel, String date);
}
