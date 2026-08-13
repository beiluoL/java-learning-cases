package com.example.ragdocqa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /**
     * 文本切片：单块最大字符数
     */
    private int chunkSize = 800;

    /**
     * 相邻块重叠字符数
     */
    private int chunkOverlap = 100;

    /**
     * 检索 Top-K
     */
    private int topK = 5;

    /**
     * 上传文件临时目录
     */
    private String uploadDir = "./uploads";
}
