package com.example.ai.config;

import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RAG 可选装配开关。
 *
 * <p>启动类默认排除 MilvusVectorStoreAutoConfiguration（它会在启动期强制连接 Milvus），
 * 让「AI Token 成本管理平台」可以脱离向量库独立运行；本类在 rag.enabled=true 时
 * 显式恢复该自动配置，配合 4 个带 @ConditionalOnProperty 的原有 RAG Bean，
 * 完整还原 MilvusLearningService / DocumentIngestionService 与两组 REST 端点。
 * 原有 RAG 代码一行未删，只是变成按需启用。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true")
@Import(MilvusVectorStoreAutoConfiguration.class)
public class RagConfiguration {
}
