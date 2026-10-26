package com.finpay.job.spi;

import com.finpay.job.model.ReconciliationBill;

import java.util.List;

/**
 * 本地订单数据源。业务系统实现，查询本系统指定日期的支付订单，转为统一账单结构。
 */
public interface LocalOrderSource {

    List<ReconciliationBill> fetchLocal(String channel, String date);
}
