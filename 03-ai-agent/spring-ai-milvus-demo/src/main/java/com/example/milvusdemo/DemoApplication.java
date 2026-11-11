package com.example.milvusdemo;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 启动类。
 * 启动后会由 spring-ai-starter-vector-store-milvus 自动装配 MilvusVectorStore，
 * 并在 Milvus 中自动创建集合（collectionName=java_learning_docs）。
 */
/**
 * 启动类。scanBasePackages 同时覆盖原有 RAG 包 com.example.milvusdemo 与新成本平台包 com.example.ai，
 * 保证「不删除、不破坏」已有 Milvus / Tika 功能。
 */
// Milvus 自动配置会在启动期强制连接向量库；成本平台默认不依赖 Milvus，
// 由 RagConfiguration 在 rag.enabled=true 时显式 @Import 恢复，原 RAG 功能完整保留。
@SpringBootApplication(scanBasePackages = {"com.example"},
        exclude = MilvusVectorStoreAutoConfiguration.class)
@MapperScan(basePackages = {
        "com.example.ai.token.usage.mapper",
        "com.example.ai.token.pricing.mapper",
        "com.example.ai.token.budget.mapper",
        "com.example.ai.token.cost.mapper",
        "com.example.ai.token.analytics.mapper",
        "com.example.ai.token.agent.mapper",
        "com.example.ai.token.infra.mapper"
})
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
