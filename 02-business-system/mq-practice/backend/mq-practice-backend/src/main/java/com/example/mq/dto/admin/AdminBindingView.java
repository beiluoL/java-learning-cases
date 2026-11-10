package com.example.mq.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** 绑定关系：排查"消息发出去了但没人收到"的第一现场（routingKey 拼错最常见） */
@Data
@Builder
public class AdminBindingView {

    private String source;

    private String destination;

    /** queue / exchange */
    private String destinationType;

    private String routingKey;

    private Map<String, Object> arguments;
}
