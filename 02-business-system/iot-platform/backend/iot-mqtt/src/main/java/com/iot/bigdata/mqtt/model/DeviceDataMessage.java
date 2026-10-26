package com.iot.bigdata.mqtt.model;

import com.iot.bigdata.common.util.JsonUtils;

import java.io.Serializable;
import java.util.Map;

/**
 * 设备上行数据点消息（MQTT 载荷的业务模型）。
 *
 * <p>示例 JSON：{@code {"deviceId":"dev-001","metric":"temperature","value":26.5,"unit":"C","ts":1690000000000}}</p>
 */
public class DeviceDataMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备编号。 */
    private String deviceId;

    /** 指标类型，如 temperature / humidity / power / voltage。 */
    private String metric;

    /** 指标数值。 */
    private double value;

    /** 单位。 */
    private String unit;

    /** 设备端时间戳（毫秒）。 */
    private long ts;

    /** 扩展字段（设备自定义）。 */
    private Map<String, Object> extra;

    public static DeviceDataMessage fromJson(String json) {
        return JsonUtils.fromJson(json, DeviceDataMessage.class);
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
}
