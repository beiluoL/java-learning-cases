package com.example.csagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 业务自定义配置（application.yml 中的 app.*）。 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final CodeBuddy codebuddy = new CodeBuddy();
    private String corsAllowedOrigins = "http://localhost:5173";

    @Data
    public static class CodeBuddy {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:3001";
        /** FAQ 检索打分阈值，低于则视为未命中 */
        private double faqScoreThreshold = 1.0;
        /** 意图识别置信度阈值 */
        private double intentConfidenceThreshold = 0.35;
    }
}
