package com.finpay.business;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 业务示例工程启动类。聚合：支付网关示例、银行交易系统示例、TCC 参与者、统一支付、安全、对账。
 * 扫描 com.finpay 下所有模块 Bean；开启调度以支持日终对账。
 */
@SpringBootApplication(scanBasePackages = "com.finpay")
@MapperScan("com.finpay.business.infrastructure.mapper")
@EnableScheduling
public class FinpayBusinessApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinpayBusinessApplication.class, args);
    }
}
