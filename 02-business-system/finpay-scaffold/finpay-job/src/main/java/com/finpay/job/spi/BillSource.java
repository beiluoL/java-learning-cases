package com.finpay.job.spi;

import com.finpay.job.model.ReconciliationBill;

import java.util.List;

/**
 * 渠道账单数据源。业务系统实现，从「渠道提供的对账单文件/API」拉取指定日期账单。
 * 例如支付宝的账单下载、微信的账单API。
 */
public interface BillSource {

    /**
     * @param channel 渠道编码
     * @param date    yyyy-MM-dd
     * @return 渠道侧账单行
     */
    List<ReconciliationBill> fetchBill(String channel, String date);
}
