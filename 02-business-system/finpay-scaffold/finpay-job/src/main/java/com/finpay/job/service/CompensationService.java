package com.finpay.job.service;

import com.finpay.job.model.Mismatch;

/**
 * 差错处理与补偿服务。对账发现的差错进入补偿队列，由调度器重试。
 */
public interface CompensationService {

    /** 登记一笔差错，生成补偿任务（如：补单 / 退款冲正 / 告警） */
    void register(Mismatch mismatch);

    /** 重试单笔补偿任务，返回是否成功 */
    boolean retry(String outTradeNo);

    /** 扫描并执行到期待重试任务 */
    void retryDue();
}
