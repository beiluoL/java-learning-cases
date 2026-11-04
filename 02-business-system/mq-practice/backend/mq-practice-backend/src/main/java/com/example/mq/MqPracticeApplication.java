package com.example.mq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Spring Boot + Vue3 全栈 MQ 实战 启动类。
 * <p>
 * 三大场景：
 * <ol>
 *   <li><b>削峰填谷</b>：秒杀下单只做 Redis 预扣 + 投递消息，落库/扣库存异步完成；</li>
 *   <li><b>延迟消息</b>：DLX + TTL 实现 30 分钟未支付自动取消；</li>
 *   <li><b>发布订阅</b>：商品变更事件被两个独立队列消费，缓存与索引各自演进。</li>
 * </ol>
 */
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@MapperScan("com.example.mq.mapper")
@SpringBootApplication
public class MqPracticeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MqPracticeApplication.class, args);
    }
}
