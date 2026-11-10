package com.example.mq.dto.admin;

import lombok.Builder;
import lombok.Data;

/**
 * 运维台连接状态：页面顶部的"能不能用"判断依据。
 * 注意 {@code ok=false} 不是异常，而是"broker 不可达"这一种业务状态 —— 页面要能优雅降级。
 */
@Data
@Builder
public class AdminStatusView {

    private boolean enabled;

    private boolean ok;

    private String baseUrl;

    /** 浏览器跳转用的原生控制台地址 */
    private String consoleUrl;

    private String version;

    private String clusterName;

    private String erlangVersion;

    private String vhost;

    /** 调用 API 的账号（只回显名字，绝不回显密码） */
    private String username;

    /** 只读 / 可写 */
    private boolean writeEnabled;

    private long queueCount;

    private long exchangeCount;

    private long consumerCount;

    private long connectionCount;

    private long totalMessages;

    private long readyMessages;

    private long unackedMessages;

    /** 失败原因（ok=false 时有值） */
    private String message;
}
