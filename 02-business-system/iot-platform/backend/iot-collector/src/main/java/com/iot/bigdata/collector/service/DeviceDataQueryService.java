package com.iot.bigdata.collector.service;

import com.iot.bigdata.collector.entity.DeviceDataPoint;
import com.iot.bigdata.collector.repository.DeviceDataPointRepository;
import com.iot.bigdata.collector.vo.DeviceDataPointVO;
import com.iot.bigdata.collector.vo.MetricStatVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备数据查询服务。封装时序数据的范围查询与聚合统计，供分析与可视化模块调用。
 */
@Service
public class DeviceDataQueryService {

    private final DeviceDataPointRepository dataPointRepository;

    public DeviceDataQueryService(DeviceDataPointRepository dataPointRepository) {
        this.dataPointRepository = dataPointRepository;
    }

    public List<DeviceDataPointVO> range(String deviceCode, String metric, LocalDateTime start, LocalDateTime end) {
        return dataPointRepository
                .findByDeviceCodeAndMetricAndCollectTimeBetween(deviceCode, metric, start, end)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public DeviceDataPointVO latest(String deviceCode, String metric) {
        return dataPointRepository.findFirstByDeviceCodeAndMetricOrderByCollectTimeDesc(deviceCode, metric)
                .map(this::toVO)
                .orElse(null);
    }

    /**
     * 单设备指标聚合（均值/最小/最大/计数）。
     */
    public List<MetricStatVO> aggregateByDevice(String deviceCode, LocalDateTime start, LocalDateTime end) {
        return dataPointRepository.aggregateByDevice(deviceCode, start, end).stream()
                .map(this::mapStat)
                .collect(Collectors.toList());
    }

    /**
     * 平台级多设备指标聚合。
     */
    public List<MetricStatVO> aggregatePlatform(LocalDateTime start, LocalDateTime end) {
        return dataPointRepository.aggregatePlatform(start, end).stream()
                .map(this::mapPlatformStat)
                .collect(Collectors.toList());
    }

    public long countByDevice(String deviceCode, LocalDateTime start, LocalDateTime end) {
        return dataPointRepository.countByDeviceCodeAndCollectTimeBetween(deviceCode, start, end);
    }

    private DeviceDataPointVO toVO(DeviceDataPoint point) {
        DeviceDataPointVO vo = new DeviceDataPointVO();
        vo.setDeviceCode(point.getDeviceCode());
        vo.setMetric(point.getMetric());
        vo.setValue(point.getMetricValue());
        vo.setUnit(point.getUnit());
        vo.setCollectTime(point.getCollectTime());
        return vo;
    }

    private MetricStatVO mapStat(Object[] row) {
        MetricStatVO vo = new MetricStatVO();
        vo.setMetric((String) row[0]);
        vo.setAvg(toBigDecimal(row[1]));
        vo.setMin(toBigDecimal(row[2]));
        vo.setMax(toBigDecimal(row[3]));
        vo.setCount(((Number) row[4]).longValue());
        return vo;
    }

    private MetricStatVO mapPlatformStat(Object[] row) {
        MetricStatVO vo = new MetricStatVO();
        vo.setDeviceCode((String) row[0]);
        vo.setMetric((String) row[1]);
        vo.setAvg(toBigDecimal(row[2]));
        vo.setMax(toBigDecimal(row[3]));
        vo.setCount(((Number) row[4]).longValue());
        return vo;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        }
        if (obj instanceof Number) {
            return BigDecimal.valueOf(((Number) obj).doubleValue());
        }
        return new BigDecimal(obj.toString());
    }
}
