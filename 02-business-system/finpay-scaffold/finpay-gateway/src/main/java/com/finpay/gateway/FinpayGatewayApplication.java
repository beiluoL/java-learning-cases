package com.finpay.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类。Spring Cloud Gateway 统一入口：路由转发、鉴权、限流、跨域。
 */
@SpringBootApplication
public class FinpayGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinpayGatewayApplication.class, args);
    }
}
