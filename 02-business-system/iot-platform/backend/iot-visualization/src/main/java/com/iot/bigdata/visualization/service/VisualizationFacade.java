package com.iot.bigdata.visualization.service;

import com.iot.bigdata.analysis.service.AnalysisFacade;
import com.iot.bigdata.collector.repository.DeviceDataPointRepository;
import com.iot.bigdata.collector.repository.DeviceRepository;
import com.iot.bigdata.collector.service.DeviceDataQueryService;
import com.iot.bigdata.collector.vo.DeviceDataPointVO;
import com.iot.bigdata.collector.vo.MetricStatVO;
import com.iot.bigdata.opinion.service.OpinionService;
import com.iot.bigdata.opinion.vo.HotTopicVO;
import com.iot.bigdata.opinion.vo.OpinionStatVO;
import com.iot.bigdata.opinion.vo.OpinionTrendVO;
import com.iot.bigdata.visualization.vo.DashboardOverviewVO;
import com.iot.bigdata.visualization.vo.MetricTrendPointVO;
import com.iot.bigdata.visualization.vo.OpinionOverviewVO;
import com.iot.bigdata.visualization.vo.StatusCountVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 可视化聚合门面。把采集 / 分析 / 舆情各模块的数据，统一编排成前端图表可直接消费的形态。
 */
@Service
public class VisualizationFacade {

    private static final DateTimeFormatter HM_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DeviceRepository deviceRepository;
    private final DeviceDataPointRepository dataPointRepository;
    private final DeviceDataQueryService dataQueryService;
    private final AnalysisFacade analysisFacade;
    private final OpinionService opinionService;

    public VisualizationFacade(DeviceRepository deviceRepository,
                               DeviceDataPointRepository dataPointRepository,
                               DeviceDataQueryService dataQueryService,
                               AnalysisFacade analysisFacade,
                               OpinionService opinionService) {
        this.deviceRepository = deviceRepository;
        this.dataPointRepository = dataPointRepository;
        this.dataQueryService = dataQueryService;
        this.analysisFacade = analysisFacade;
        this.opinionService = opinionService;
    }

    /**
     * 总览看板：跨模块聚合的平台运行态势。
     */
    public DashboardOverviewVO overview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusHours(24);

        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setDeviceTotal(deviceRepository.count());
        vo.setDeviceOnline(deviceRepository.countByStatus("ONLINE"));
        vo.setDeviceOffline(deviceRepository.countByStatus("OFFLINE"));
        vo.setDataPointCount24h(dataPointRepository.countByCollectTimeBetween(dayAgo, now));
        vo.setAlertCount(analysisFacade.recentAlerts(1000).size());
        OpinionStatVO opinionStat = opinionService.statistics();
        vo.setOpinionTotal(opinionStat.getTotal());
        vo.setSentimentIndex(opinionStat.getSentimentIndex());
        vo.setTime(now);
        return vo;
    }

    /**
     * 设备指标趋势（最近 hours 小时）。
     */
    public List<MetricTrendPointVO> deviceMetricTrend(String deviceCode, String metric, int hours) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(hours);
        List<DeviceDataPointVO> points = dataQueryService.range(deviceCode, metric, start, end);
        List<MetricTrendPointVO> result = new ArrayList<>();
        for (DeviceDataPointVO p : points) {
            MetricTrendPointVO point = new MetricTrendPointVO();
            point.setTime(p.getCollectTime() == null ? "" : p.getCollectTime().format(HM_FORMATTER));
            point.setValue(p.getValue());
            result.add(point);
        }
        return result;
    }

    /**
     * 设备状态分布（饼图数据）。
     */
    public List<StatusCountVO> deviceStatusDistribution() {
        List<StatusCountVO> list = new ArrayList<>();
        StatusCountVO online = new StatusCountVO();
        online.setStatus("ONLINE");
        online.setCount(deviceRepository.countByStatus("ONLINE"));
        StatusCountVO offline = new StatusCountVO();
        offline.setStatus("OFFLINE");
        offline.setCount(deviceRepository.countByStatus("OFFLINE"));
        list.add(online);
        list.add(offline);
        return list;
    }

    /**
     * 单设备指标聚合统计。
     */
    public List<MetricStatVO> metricAggregate(String deviceCode, int hours) {
        LocalDateTime end = LocalDateTime.now();
        return dataQueryService.aggregateByDevice(deviceCode, end.minusHours(hours), end);
    }

    /**
     * 舆情总览。
     */
    public OpinionOverviewVO opinionOverview() {
        OpinionOverviewVO vo = new OpinionOverviewVO();
        vo.setStat(opinionService.statistics());
        vo.setTrend(opinionService.trend(7));
        vo.setHotTopics(opinionService.hotTopics(7, 10));
        return vo;
    }
}
