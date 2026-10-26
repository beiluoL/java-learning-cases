package com.finpay.job.task;

import com.finpay.job.model.Mismatch;
import com.finpay.job.model.ReconcileReport;
import com.finpay.job.service.CompensationService;
import com.finpay.job.service.ReconciliationService;
import com.finpay.payment.core.api.ChannelConfigProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 日终对账调度。每日 02:30 对全部已配置渠道执行对账，发现差错即登记补偿。
 * 需在业务工程主类上开启 @EnableScheduling。
 */
@Slf4j
@Component
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;
    private final CompensationService compensationService;
    private final ChannelConfigProvider channelConfigProvider;

    public ReconciliationScheduler(ReconciliationService reconciliationService,
                                   CompensationService compensationService,
                                   ChannelConfigProvider channelConfigProvider) {
        this.reconciliationService = reconciliationService;
        this.compensationService = compensationService;
        this.channelConfigProvider = channelConfigProvider;
    }

    @Scheduled(cron = "0 30 2 * * ?")
    public void dailyReconcile() {
        String yesterday = LocalDate.now().minusDays(1).toString();
        List<String> channels = channelConfigProvider.listChannels();
        for (String ch : channels) {
            try {
                ReconcileReport report = reconciliationService.reconcile(ch, yesterday);
                log.info("[对账] 渠道={} 日期={} 本地={} 渠道={} 匹配={} 差错={}",
                        ch, yesterday, report.getLocalCount(), report.getChannelCount(),
                        report.getMatchedCount(), report.getMismatchCount());
                for (Mismatch m : report.getMismatches()) {
                    compensationService.register(m);
                }
            } catch (Exception e) {
                log.error("[对账] 渠道={} 失败", ch, e);
            }
        }
    }

    /** 补偿重试调度：每 5 分钟执行一次 */
    @Scheduled(cron = "0 */5 * * * ?")
    public void retryCompensation() {
        compensationService.retryDue();
    }
}
