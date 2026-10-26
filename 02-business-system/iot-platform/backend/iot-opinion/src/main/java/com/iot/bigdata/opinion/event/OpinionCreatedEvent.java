package com.iot.bigdata.opinion.event;

import com.iot.bigdata.opinion.vo.OpinionVO;
import org.springframework.context.ApplicationEvent;

/**
 * 舆情创建事件。新舆情入库后发布，供 WebSocket 实时推送与下游消费。
 */
public class OpinionCreatedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final OpinionVO opinion;

    public OpinionCreatedEvent(Object source, OpinionVO opinion) {
        super(source);
        this.opinion = opinion;
    }

    public OpinionVO getOpinion() {
        return opinion;
    }
}
