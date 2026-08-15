package com.iot.bigdata.web.controller;

import com.iot.bigdata.analysis.service.AnalysisFacade;
import com.iot.bigdata.common.result.ApiResult;
import com.iot.bigdata.visualization.service.VisualizationFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 总览看板接口。聚合采集 / 分析 / 舆情数据，供前端总览页消费。
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final VisualizationFacade visualizationFacade;
    private final AnalysisFacade analysisFacade;

    public DashboardController(VisualizationFacade visualizationFacade, AnalysisFacade analysisFacade) {
        this.visualizationFacade = visualizationFacade;
        this.analysisFacade = analysisFacade;
    }

    @GetMapping("/overview")
    public ApiResult<?> overview() {
        return ApiResult.success(visualizationFacade.overview());
    }

    @GetMapping("/opinion")
    public ApiResult<?> opinion() {
        return ApiResult.success(visualizationFacade.opinionOverview());
    }

    @GetMapping("/device-status")
    public ApiResult<?> deviceStatus() {
        return ApiResult.success(visualizationFacade.deviceStatusDistribution());
    }

    @GetMapping("/metric-trend")
    public ApiResult<?> metricTrend(@RequestParam String deviceCode,
                                    @RequestParam String metric,
                                    @RequestParam(defaultValue = "6") int hours) {
        return ApiResult.success(visualizationFacade.deviceMetricTrend(deviceCode, metric, hours));
    }

    @GetMapping("/alerts")
    public ApiResult<?> alerts(@RequestParam(defaultValue = "20") int n) {
        return ApiResult.success(analysisFacade.recentAlerts(n));
    }
}
