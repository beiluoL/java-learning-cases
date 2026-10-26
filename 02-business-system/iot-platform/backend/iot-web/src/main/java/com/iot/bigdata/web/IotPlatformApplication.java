package com.iot.bigdata.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IoT 大数据平台主启动类。
 *
 * <p>扫描根包 {@code com.iot.bigdata} 以加载所有模块组件；
 * 显式指定 {@code @EntityScan} 与 {@code @EnableJpaRepositories} 让 JPA 托管跨模块实体与仓库。</p>
 */
@SpringBootApplication(scanBasePackages = "com.iot.bigdata")
@EntityScan("com.iot.bigdata")
@EnableJpaRepositories("com.iot.bigdata")
@EnableScheduling
public class IotPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotPlatformApplication.class, args);
    }
}
