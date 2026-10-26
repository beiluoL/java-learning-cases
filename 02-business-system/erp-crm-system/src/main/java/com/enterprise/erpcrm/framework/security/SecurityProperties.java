package com.enterprise.erpcrm.framework.security;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全相关配置绑定（application.yml 的 {@code erp.security.*}）。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "erp.security")
public class SecurityProperties {

    /** 免认证白名单（Ant 风格） */
    private List<String> ignoreUrls = List.of();
    /** 超级管理员角色标识 */
    private String superAdminRole = "SUPER_ADMIN";
}
