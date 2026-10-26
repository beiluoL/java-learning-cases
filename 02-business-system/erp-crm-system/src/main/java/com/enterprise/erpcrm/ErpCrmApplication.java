package com.enterprise.erpcrm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ERP + CRM 综合管理系统启动类。
 * <p>
 * 技术栈：Spring Boot 3.4 + MyBatis-Plus 3.5 + Knife4j(OpenAPI3) + JWT + EasyExcel + Caffeine。
 * 数据库通过 profile 切换：{@code h2}（默认，克隆即跑）/ {@code mysql}（生产形态）。
 * </p>
 *
 * @author erp-crm-system
 */
@EnableCaching
@EnableAsync
@SpringBootApplication
@MapperScan("com.enterprise.erpcrm.module.*.mapper")
public class ErpCrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpCrmApplication.class, args);
    }
}
