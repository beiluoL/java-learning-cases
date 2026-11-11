package com.example.milvusdemo;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类（企业级 AI 成本治理平台）。
 *
 * <p>扫描范围显式限定为两个包，避免 `com.example` 全量扫描带来意外装配：
 * <ul>
 *   <li>{@code com.example.ai} —— 成本治理平台主体（10 个业务域）。</li>
 *   <li>{@code com.example.milvusdemo} —— 保留的 RAG / Milvus 遗留上下文。</li>
 * </ul>
 * </p>
 *
 * <p>Milvus 自动配置会在启动期强制连接向量库，而成本平台默认不依赖 Milvus，
 * 因此显式排除 {@link MilvusVectorStoreAutoConfiguration}；
 * 需要 RAG 时由 {@code RagConfiguration} 在 {@code rag.enabled=true} 下显式 @Import 恢复。</p>
 */
// @MapperScan 不再硬编码各业务域的 mapper 子包（原做法在包移动时会静默失效，
// 启动期才报 Invalid bound statement）。改为「基包 + @Mapper 注解过滤」：
// 只有显式标注 @Mapper 的接口才会被注册，因此 identity/provider 等普通接口不会被误注册。
@SpringBootApplication(scanBasePackages = {"com.example.ai", "com.example.milvusdemo"},
        exclude = MilvusVectorStoreAutoConfiguration.class)
@MapperScan(basePackages = "com.example.ai", annotationClass = Mapper.class)
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
