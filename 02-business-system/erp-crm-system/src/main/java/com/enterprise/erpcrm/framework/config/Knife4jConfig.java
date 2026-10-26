package com.enterprise.erpcrm.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j + OpenAPI 3）。
 * <p>访问地址：http://localhost:8081/doc.html （Swagger 原生 UI：/swagger-ui.html）。</p>
 *
 * @author erp-crm-system
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI erpOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ERP-CRM 综合管理系统 API")
                .description("企业级 ERP + CRM：进销存 / HRM / CRM + RBAC + 审批流 + 报表 + 导入导出")
                .version("v1.0.0")
                .contact(new Contact().name("erp-crm-system").email("admin@erp.com")));
    }
}
