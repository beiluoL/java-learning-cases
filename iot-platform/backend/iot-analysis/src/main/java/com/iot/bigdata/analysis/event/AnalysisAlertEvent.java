package com.iot.bigdata.analysis.event;

import com.iot.bigdata.analysis.model.AnalysisAlert;
import org.springframework.context.ApplicationEvent;

/**
 * 分析告警事件。异常检测命中后发布，供 WebSocket 推送与告警存储消费。
 */
public class AnalysisAlertEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final AnalysisAlert alert;

    public AnalysisAlertEvent(Object source, AnalysisAlert alert) {
        super(source);
        this.alert = alert;
    }

    public AnalysisAlert getAlert() {
        return alert;
    }
}
