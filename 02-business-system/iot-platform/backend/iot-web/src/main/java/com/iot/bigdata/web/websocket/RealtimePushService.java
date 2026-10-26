package com.iot.bigdata.web.websocket;

import com.iot.bigdata.analysis.event.AnalysisAlertEvent;
import com.iot.bigdata.common.constant.IotConstants;
import com.iot.bigdata.mqtt.event.DeviceDataReceivedEvent;
import com.iot.bigdata.opinion.event.OpinionCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 实时推送服务。监听各业务事件，通过 STOMP 将设备指标、告警、舆情推送到前端看板。
 */
@Component
public class RealtimePushService {

    private static final Logger log = LoggerFactory.getLogger(RealtimePushService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onDeviceData(DeviceDataReceivedEvent event) {
        messagingTemplate.convertAndSend(IotConstants.WS_TOPIC_DEVICE_METRICS, event.getMessage());
    }

    @EventListener
    public void onAlert(AnalysisAlertEvent event) {
        messagingTemplate.convertAndSend(IotConstants.WS_TOPIC_ALERT, event.getAlert());
        log.debug("[WS] 推送告警 device={}", event.getAlert().getDeviceCode());
    }

    @EventListener
    public void onOpinion(OpinionCreatedEvent event) {
        messagingTemplate.convertAndSend(IotConstants.WS_TOPIC_OPINION, event.getOpinion());
    }
}
