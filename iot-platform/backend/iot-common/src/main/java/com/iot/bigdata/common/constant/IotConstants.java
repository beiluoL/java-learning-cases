package com.iot.bigdata.common.constant;

/**
 * 项目级常量定义。集中管理魔法值，避免散落在业务代码中（阿里规约：禁止魔法值）。
 */
public final class IotConstants {

    private IotConstants() {
    }

    /** 默认分页大小。 */
    public static final long DEFAULT_PAGE_SIZE = 20L;

    /** 默认页码。 */
    public static final long DEFAULT_PAGE_NUM = 1L;

    /** MQTT 默认监听端口。 */
    public static final int MQTT_DEFAULT_PORT = 1883;

    /** MQTT 设备上行数据主题模板：iot/device/{deviceId}/data。 */
    public static final String MQTT_TOPIC_DEVICE_DATA = "iot/device/+/data";

    /** MQTT 设备命令下发主题模板：iot/device/{deviceId}/command。 */
    public static final String MQTT_TOPIC_DEVICE_COMMAND = "iot/device/%s/command";

    /** WebSocket 实时指标推送地址（STOMP）。 */
    public static final String WS_TOPIC_DEVICE_METRICS = "/topic/device-metrics";

    /** WebSocket 舆情推送地址（STOMP）。 */
    public static final String WS_TOPIC_OPINION = "/topic/opinion";

    /** WebSocket 告警推送地址（STOMP）。 */
    public static final String WS_TOPIC_ALERT = "/topic/alert";

    /** 逻辑删除：未删除。 */
    public static final int DELETED_NO = 0;

    /** 逻辑删除：已删除。 */
    public static final int DELETED_YES = 1;
}
