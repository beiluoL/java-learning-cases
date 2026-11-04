package com.example.mq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 故障注入开关。
 * <p>
 * 学习 MQ 最有效的方式不是读文档，而是<b>亲手把消费者打挂</b>，然后观察：
 * 消息是否进了重试队列 → 是否按退避节奏回来 → 3 次后是否落到死信 → 补偿回放后是否恢复。
 * 这个开关就是为此而生（生产环境当然不允许存在这种接口）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChaosConfig {

    /** 场景名：ORDER_CREATE / ORDER_CANCEL / PRODUCT_CACHE_SYNC / PRODUCT_SEARCH_SYNC */
    private String scene;

    /** 是否开启故障注入 */
    private boolean enabled;

    /** 失败概率 0-100 */
    private int failRate;

    /** 当前各场景状态 */
    private Map<String, ChaosConfig> all;
}
