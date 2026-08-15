package com.iot.bigdata.mqtt.gateway;

import com.iot.bigdata.common.constant.IotConstants;
import com.iot.bigdata.mqtt.client.MqttClientManager;
import com.iot.bigdata.mqtt.model.DeviceDataMessage;
import com.iot.bigdata.common.util.JsonUtils;
import org.springframework.stereotype.Component;

/**
 * MQTT 网关。封装向设备下发命令、推送模拟数据等高层能力，
 * 业务模块（如 Web 层模拟器、指令接口）通过本类与 Broker 交互，无需感知底层客户端。
 */
@Component
public class MqttGateway {

    private final MqttClientManager clientManager;

    public MqttGateway(MqttClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * 向指定设备下发命令。
     *
     * @param deviceId 设备编号
     * @param command  命令内容（JSON 字符串）
     */
    public void sendCommand(String deviceId, String command) {
        String topic = String.format(IotConstants.MQTT_TOPIC_DEVICE_COMMAND, deviceId);
        clientManager.publish(topic, command, 1);
    }

    /**
     * 向指定设备推送一条模拟数据点（用于演示与测试）。
     *
     * @param message 数据点消息
     */
    public void publishDeviceData(DeviceDataMessage message) {
        if (message.getTs() == 0) {
            message.setTs(System.currentTimeMillis());
        }
        String topic = "iot/device/" + message.getDeviceId() + "/data";
        // 设备遥测为高频流数据，采用 QoS 0（至多一次）即可：不占用 Paho 的 in-flight 窗口，
        // 避免批量发布时触发 MAX_INFLIGHT 异常；回环订阅仍可正常收到并进入采集→分析链路。
        clientManager.publish(topic, JsonUtils.toJson(message), 0);
    }
}
