package com.iot.bigdata.analysis.store;

import com.iot.bigdata.analysis.model.DeviceMetricSnapshot;
import com.iot.bigdata.analysis.util.StatisticsUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时指标存储。维护每个“设备+指标”的滚动窗口与最新快照，支撑看板实时读取。
 *
 * <p>学习项目采用内存存储；生产环境可替换为 Redis TimeSeries / InfluxDB 等时序库。</p>
 */
@Service
public class RealtimeMetricsService {

    /** 滚动窗口点数（约最近 120 个采样，配合采样频率即时间窗）。 */
    private static final int WINDOW_SIZE = 120;

    private final Map<String, DeviceMetricSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, ArrayDeque<Double>> buffers = new ConcurrentHashMap<>();

    private static String keyOf(String deviceCode, String metric) {
        return deviceCode + "|" + metric;
    }

    /**
     * 写入一条实时数据点，更新滚动窗口与快照。
     */
    public DeviceMetricSnapshot onData(String deviceCode, String metric, double value, String unit) {
        String key = keyOf(deviceCode, metric);
        ArrayDeque<Double> buf = buffers.computeIfAbsent(key, k -> new ArrayDeque<>());
        List<Double> snapshotCopy;
        synchronized (buf) {
            buf.addLast(value);
            if (buf.size() > WINDOW_SIZE) {
                buf.removeFirst();
            }
            snapshotCopy = new ArrayList<>(buf);
        }

        double mean = StatisticsUtil.mean(snapshotCopy);
        double min = StatisticsUtil.min(snapshotCopy);
        double max = StatisticsUtil.max(snapshotCopy);

        DeviceMetricSnapshot snap = new DeviceMetricSnapshot();
        snap.setDeviceCode(deviceCode);
        snap.setMetric(metric);
        snap.setUnit(unit);
        snap.setCurrentValue(BigDecimal.valueOf(value));
        snap.setAvg(BigDecimal.valueOf(mean));
        snap.setMin(BigDecimal.valueOf(min));
        snap.setMax(BigDecimal.valueOf(max));
        snap.setCount(snapshotCopy.size());
        snap.setStatus("NORMAL");
        snap.setLastUpdate(LocalDateTime.now());
        snapshots.put(key, snap);
        return snap;
    }

    public void updateStatus(String deviceCode, String metric, String status) {
        DeviceMetricSnapshot snap = snapshots.get(keyOf(deviceCode, metric));
        if (snap != null) {
            snap.setStatus(status);
        }
    }

    public DeviceMetricSnapshot getSnapshot(String deviceCode, String metric) {
        return snapshots.get(keyOf(deviceCode, metric));
    }

    public List<DeviceMetricSnapshot> getAll() {
        return new ArrayList<>(snapshots.values());
    }

    public List<DeviceMetricSnapshot> getByDevice(String deviceCode) {
        List<DeviceMetricSnapshot> result = new ArrayList<>();
        for (DeviceMetricSnapshot snap : snapshots.values()) {
            if (deviceCode.equals(snap.getDeviceCode())) {
                result.add(snap);
            }
        }
        return result;
    }
}
