package com.finpay.business.web.gateway;

import com.finpay.common.result.Result;
import com.finpay.job.model.ReconcileReport;
import com.finpay.job.service.CompensationService;
import com.finpay.job.service.ReconciliationService;
import com.finpay.security.audit.AuditLog;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 对账管理（管理后台「对账管理」页）。支持手动触发指定渠道/日期对账，并查看差错报告。
 */
@RestController
@RequestMapping("/api/admin/reconcile")
@CrossOrigin
public class ReconcileController {

    private final ReconciliationService reconciliationService;
    private final CompensationService compensationService;

    public ReconcileController(ReconciliationService reconciliationService, CompensationService compensationService) {
        this.reconciliationService = reconciliationService;
        this.compensationService = compensationService;
    }

    @PostMapping("/run")
    @AuditLog(action = "RECONCILE", resource = "RECONCILE", logParams = false)
    public Result<?> run(@RequestParam String channel,
                         @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().minusDays(1)}") String date) {
        String d = date.startsWith("#") ? LocalDate.now().minusDays(1).toString() : date;
        ReconcileReport report = reconciliationService.reconcile(channel, d);
        report.getMismatches().forEach(compensationService::register);
        return Result.success(report);
    }

    @PostMapping("/retry")
    public Result<?> retry(@RequestParam String outTradeNo) {
        return Result.success(compensationService.retry(outTradeNo));
    }
}
