package com.iot.bigdata.collector.repository;

import com.iot.bigdata.collector.entity.DeviceDataPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 设备数据点数据访问层。提供范围查询与聚合统计（供可视化/分析模块使用）。
 */
@Repository
public interface DeviceDataPointRepository
        extends JpaRepository<DeviceDataPoint, Long>, JpaSpecificationExecutor<DeviceDataPoint> {

    List<DeviceDataPoint> findByDeviceCodeAndMetricAndCollectTimeBetween(
            String deviceCode, String metric, LocalDateTime start, LocalDateTime end);

    Optional<DeviceDataPoint> findFirstByDeviceCodeAndMetricOrderByCollectTimeDesc(String deviceCode, String metric);

    long countByDeviceCodeAndCollectTimeBetween(String deviceCode, LocalDateTime start, LocalDateTime end);

    long countByCollectTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 按指标聚合统计（均值/最小/最大/计数/最新值）。
     *
     * @return 每行 [metric, avg, min, max, count]
     */
    @Query("SELECT d.metric, AVG(d.metricValue), MIN(d.metricValue), MAX(d.metricValue), COUNT(d) "
            + "FROM DeviceDataPoint d "
            + "WHERE d.deviceCode = :deviceCode AND d.collectTime BETWEEN :start AND :end "
            + "GROUP BY d.metric")
    List<Object[]> aggregateByDevice(@Param("deviceCode") String deviceCode,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * 平台级多设备指标聚合（用于总览看板）。
     *
     * @return 每行 [deviceCode, metric, avg, max, count]
     */
    @Query("SELECT d.deviceCode, d.metric, AVG(d.metricValue), MAX(d.metricValue), COUNT(d) "
            + "FROM DeviceDataPoint d "
            + "WHERE d.collectTime BETWEEN :start AND :end "
            + "GROUP BY d.deviceCode, d.metric")
    List<Object[]> aggregatePlatform(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
