package com.example.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 可视化管理台的接入配置（{@code mq.practice.admin.*}）。
 *
 * <h3>为什么要做成"后端代理"而不是前端直连 15672</h3>
 * <ol>
 *   <li><b>凭据安全</b>：Management API 用 HTTP Basic，凭据一旦进浏览器就等于公开；</li>
 *   <li><b>跨域</b>：15672 与 5173 不同源，浏览器直连会被 CORS 拦掉；</li>
 *   <li><b>收敛出口</b>：只有后端能访问 broker，可以把 15672 绑在 127.0.0.1 或内网，
 *       公网只暴露应用端口，攻击面从"两个端口"缩到"一个端口"；</li>
 *   <li><b>可审计</b>：所有调用都过一次后端日志，谁在什么时候 peek/purge 了哪个队列有迹可循。</li>
 * </ol>
 */
@Data
@Component
@ConfigurationProperties(prefix = "mq.practice.admin")
public class MqAdminProperties {

    /** 总开关：关掉后所有 /api/mq/admin/** 返回"未启用" */
    private boolean enabled = true;

    /** Management API 基址（服务端访问，不要写公网地址） */
    private String baseUrl = "http://localhost:15672";

    /** 浏览器里"打开原生控制台"按钮跳转的地址 */
    private String consoleUrl = "http://localhost:15672";

    /** 调用 Management API 的账号。生产建议用 monitoring 标签的只读账号 */
    private String username = "guest";

    private String password = "guest";

    /** 目标 vhost，默认 "/" */
    private String vhost = "/";

    /** 连接/读取超时（毫秒）。broker 卡住时不能让运维页面把线程拖死 */
    private int timeoutMs = 3000;

    /**
     * 写操作（清空队列、投递测试消息）总开关。
     * 演示环境默认开启；生产务必置 false，或改成"仅特定角色可写"。
     */
    private boolean writeEnabled = true;

    /**
     * 运维口令：写操作必须带 X-MQ-Admin-Token 且与之相等。
     * 留空 = 一律拒绝写操作（fail-closed，比放行安全）。
     * 生产请通过环境变量覆盖：MQ_ADMIN_WRITE_TOKEN=xxx
     */
    private String writeToken = "mq-practice";
}
