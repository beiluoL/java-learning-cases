package com.example.milvusdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动类。
 * 启动后会由 spring-ai-starter-vector-store-milvus 自动装配 MilvusVectorStore，
 * 并在 Milvus 中自动创建集合（collectionName=java_learning_docs）。
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
