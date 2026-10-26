package com.iot.bigdata.web.controller;

import com.iot.bigdata.analysis.service.AnalysisFacade;
import com.iot.bigdata.common.result.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实时分析接口。提供实时指标快照、告警与阈值规则查询。
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisFacade analysisFacade;

    public AnalysisController(AnalysisFacade analysisFacade) {
        this.analysisFacade = analysisFacade;
    }

    @GetMapping("/realtime")
    public ApiResult<?> realtime(@RequestParam(required = false) String deviceCode) {
        if (deviceCode == null || deviceCode.isEmpty()) {
            return ApiResult.success(analysisFacade.realtimeSnapshots());
        }
        return ApiResult.success(analysisFacade.snapshotsByDevice(deviceCode));
    }

    @GetMapping("/alerts")
    public ApiResult<?> alerts(@RequestParam(defaultValue = "20") int n) {
        return ApiResult.success(analysisFacade.recentAlerts(n));
    }

    @GetMapping("/rules")
    public ApiResult<?> rules() {
        return ApiResult.success(analysisFacade.rules());
    }
}
