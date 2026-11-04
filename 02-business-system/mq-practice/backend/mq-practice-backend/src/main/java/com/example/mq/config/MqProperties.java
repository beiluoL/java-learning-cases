package com.example.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务侧 MQ 参数（application.yml 的 {@code mq.practice.*}）。
 * 把这些值外部化，是为了让「延迟时长 / 重试次数 / 延迟实现方式」在演示、压测、生产三种场景下
 * 只改配置不改代码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mq.practice")
public class MqProperties {

    /** 订单支付超时（秒），默认 30 分钟。前端演示时可传更小的值覆盖 */
    private int orderDelaySeconds = 1800;

    /** 消费失败最大重试次数，超过后进入死信队列 */
    private int maxRetryCount = 3;

    /** 重试队列 TTL（毫秒），失败消息延迟这么久后再回投业务队列 */
    private long retryDelayMs = 5000;

    /** 幂等键在 Redis 中的存活时间（小时） */
    private int idempotentTtlHours = 24;

    /** 商品详情缓存 TTL（分钟） */
    private int productCacheTtlMinutes = 30;

    /** 延迟消息实现：dlx（DLX+TTL，零插件）/ plugin（x-delayed-message 插件） */
    private String delayMode = "dlx";

    /** 幂等总开关，关掉后只剩数据库唯一键兜底（课堂对比用） */
    private boolean idempotentEnabled = true;
}
