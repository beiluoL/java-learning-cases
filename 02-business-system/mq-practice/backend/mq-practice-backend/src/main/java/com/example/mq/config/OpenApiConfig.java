package com.example.mq.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 文档信息（访问 /swagger-ui.html） */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mqPracticeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MQ 消息队列实战 API")
                .version("1.0.0")
                .description("""
                        Spring Boot 3 + Vue3 全栈 MQ 实战项目接口文档。

                        三大场景：
                        1. 秒杀下单（削峰填谷 + 异步解耦 + 防超卖）
                        2. 订单超时取消（DLX + TTL 延迟消息）
                        3. 商品变更发布订阅（一次事件，两个独立消费者）

                        可靠性设施：Publisher Confirm / Return、消费端手动 ACK、幂等表、
                        重试队列、死信队列与死信回放。
                        """)
                .contact(new Contact().name("mq-practice")));
    }
}
