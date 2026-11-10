package com.example.mq.dto.admin;

import lombok.Builder;
import lombok.Data;

/** 连接视图：排查连接泄漏（channels 只增不减）与客户端版本问题 */
@Data
@Builder
public class AdminConnectionView {

    private String name;

    private String user;

    private String peerHost;

    private String state;

    private long channels;

    private double recvRate;

    private double sendRate;

    /** AMQP 0-9-1 等 */
    private String protocol;

    private boolean ssl;

    /** 客户端声明的 product/version（Spring AMQP 会带） */
    private String clientInfo;
}
